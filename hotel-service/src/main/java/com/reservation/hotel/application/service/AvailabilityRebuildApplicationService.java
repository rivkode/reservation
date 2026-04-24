package com.reservation.hotel.application.service;

import com.reservation.hotel.application.dto.InventoryRebuildEntry;
import com.reservation.hotel.application.dto.RebuildResult;
import com.reservation.hotel.application.port.InventorySnapshotSource;
import com.reservation.hotel.application.port.RoomAvailabilityRebuilder;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.repository.HotelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FR-H-08 — 가용성 캐시 재구축 orchestrator. 모든 호텔을 순회하며 reservation-service SoT
 * 에서 Inventory 스냅샷을 받아 Redis Hash 에 upsert 한다.
 *
 * <p>호출 경로 두 개(기동 시 · 일일 새벽 배치) 가 동시에 돌지 않도록 {@link AtomicBoolean} 로
 * 단일 인스턴스 동시성 가드를 둔다. 여러 인스턴스(multi-replica) 간 조율은 본 PR 범위 외 —
 * Redis 쓰기가 idempotent (HSET 덮어쓰기) 이므로 중복 실행이 정합성을 깨지 않고 자원만
 * 낭비할 뿐이다.
 *
 * <p>한 호텔의 gRPC 실패는 나머지 호텔 처리를 막지 않는다 — 실패 카운터만 올리고 계속 진행.
 * {@link RoomAvailabilityRebuilder#upsertAll(List) rebuilder.upsertAll} 의 pipeline 이 중간
 * 실패하면 해당 호텔은 부분 쓰기 상태로 남지만, 다음 daily tick 이 동일 entry 를 덮어써
 * 수렴 복구한다. 호텔 단위 격리로 한 호텔의 pipeline 실패가 다른 호텔로 전염되지 않는다.
 *
 * <p><strong>Scope note — 과거 key 삭제 미포함</strong>: ADR 0004 의 "90일 외 key 는 일일
 * 배치가 삭제" 정책은 본 PR 에서 구현하지 않는다. Phase 1 규모에서 Redis 메모리 압력은
 * 낮고, 운영 진입 전 별도 PR 에서 {@code SCAN + UNLINK} 로 정리할 예정.
 */
@Slf4j
@Service
public class AvailabilityRebuildApplicationService {

    private final HotelRepository hotelRepository;
    private final InventorySnapshotSource snapshotSource;
    private final RoomAvailabilityRebuilder rebuilder;
    private final Clock clock;
    private final int horizonDays;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public AvailabilityRebuildApplicationService(HotelRepository hotelRepository,
                                                 InventorySnapshotSource snapshotSource,
                                                 RoomAvailabilityRebuilder rebuilder,
                                                 Clock clock,
                                                 @Value("${app.availability.rebuild.horizon-days:90}") int horizonDays) {
        this.hotelRepository = hotelRepository;
        this.snapshotSource = snapshotSource;
        this.rebuilder = rebuilder;
        this.clock = clock;
        if (horizonDays <= 0) {
            throw new IllegalArgumentException(
                "app.availability.rebuild.horizon-days must be positive, was " + horizonDays);
        }
        this.horizonDays = horizonDays;
    }

    /**
     * 전체 호텔의 Redis 가용성 캐시를 재구축한다. 이미 실행 중이면 즉시 {@code RebuildResult.empty()}
     * 를 반환하고 stale 한 두 번째 트리거는 무시. 예외는 전파하지 않고 요약 Result 로 반환.
     */
    public RebuildResult rebuildAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Rebuild already in progress — skipping this trigger");
            return RebuildResult.empty();
        }

        try {
            List<HotelId> hotelIds = hotelRepository.findAllIds();
            LocalDate today = LocalDate.now(clock);
            // reservation.proto 의 from_date/to_date 는 둘 다 inclusive 이므로 90일 범위는
            // [today, today + 89] 가 된다. "horizonDays 일"은 "오늘 포함 N 일치" 의미.
            LocalDate untilInclusive = today.plusDays(horizonDays - 1);

            int processed = 0;
            int failed = 0;
            int totalEntries = 0;
            for (HotelId hotelId : hotelIds) {
                try {
                    List<InventoryRebuildEntry> entries =
                        snapshotSource.findByHotelInRange(hotelId, today, untilInclusive);
                    if (!entries.isEmpty()) {
                        rebuilder.upsertAll(entries);
                    }
                    totalEntries += entries.size();
                    processed++;
                } catch (InventorySnapshotSource.InventoryStreamUnavailableException e) {
                    log.warn("Rebuild failed for hotelId={} — continuing with others",
                        hotelId.asString(), e);
                    failed++;
                } catch (RuntimeException e) {
                    log.error("Unexpected rebuild failure for hotelId={}",
                        hotelId.asString(), e);
                    failed++;
                }
            }
            log.info("Rebuild complete: hotels={} processed={} failed={} entries={}",
                hotelIds.size(), processed, failed, totalEntries);
            return new RebuildResult(processed, failed, totalEntries);
        } finally {
            running.set(false);
        }
    }
}

package com.reservation.reservation.application.service;

import com.reservation.reservation.application.dto.InventorySnapshotResult;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * hotel-service 의 가용성 Read Model 재구축 배치(FR-H-08 · PR-3.3) 를 위한 읽기 전용
 * Use Case. SoT 인 {@link RoomTypeInventory} 를 호텔 단위 · 날짜 범위로 조회해 상위
 * 계층(gRPC server) 이 스트리밍할 수 있도록 DTO 리스트로 반환한다.
 *
 * <p>경계 방어:
 * <ul>
 *   <li>{@code from > to} — {@link IllegalArgumentException}. 빈 결과로 조용히 처리하면
 *       호출자 버그가 drift 될 수 있어 명시적 차단.</li>
 *   <li>범위가 {@code maxRangeDays} 초과 — {@link IllegalArgumentException}. 기본값 400 일 은
 *       hotel-service 캐시 정책(90일) 대비 여유를 두되, 인증 없는 내부 gRPC 의 DoS 방어선
 *       역할을 겸한다. 초과 시 호출자가 날짜 범위를 slice 해서 다시 호출한다.
 *       값은 {@code app.inventory.stream.max-range-days} yml 프로퍼티로 환경별 조정 가능.</li>
 * </ul>
 *
 * <p>{@code @Transactional(readOnly = true)} 로 쓰기 방지 + Hibernate flush 생략.
 * 존재하지 않는 호텔 / 빈 구간은 에러 아닌 빈 리스트로 반환해 "객실 미등록 호텔" 같은
 * 정상 시나리오를 흡수한다.
 *
 * <p>설계 노트 (PR-3.3 연계) — 현재 구현은 전체 결과를 메모리 List 로 모아 한 번에 반환한다.
 * PR-3.3 에서 실제 hotel-service 배치 호출량이 확정되면 Repository 반환을 {@code Stream}
 * 또는 JPA {@code Slice} 로 교체하고, gRPC 서버 측에 {@code ServerCallStreamObserver}
 * 백프레셔를 적용할 여지가 있다. 현재 범위(400일 · 호텔당 수천 row) 에서는 List 적재 비용이
 * 허용 범위이며 후속 PR 의 실제 프로파일링 결과로 결정한다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class InventoryQueryApplicationService {

    private final RoomTypeInventoryRepository inventoryRepository;
    private final int maxRangeDays;

    public InventoryQueryApplicationService(
        RoomTypeInventoryRepository inventoryRepository,
        @Value("${app.inventory.stream.max-range-days:400}") int maxRangeDays
    ) {
        this.inventoryRepository = inventoryRepository;
        if (maxRangeDays <= 0) {
            throw new IllegalArgumentException(
                "app.inventory.stream.max-range-days must be positive, was " + maxRangeDays);
        }
        this.maxRangeDays = maxRangeDays;
    }

    public int maxRangeDays() {
        return maxRangeDays;
    }

    public List<InventorySnapshotResult> findByHotelInRange(HotelId hotelId,
                                                             LocalDate from,
                                                             LocalDate to) {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                "from must not be after to: from=" + from + ", to=" + to);
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > maxRangeDays) {
            throw new IllegalArgumentException(
                "range too large: max=" + maxRangeDays + " days, was=" + days);
        }

        List<RoomTypeInventory> inventories =
            inventoryRepository.findRangeByHotel(hotelId, from, to);
        return inventories.stream().map(InventorySnapshotResult::from).toList();
    }
}

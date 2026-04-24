package com.reservation.hotel.infrastructure.cache;

import com.reservation.hotel.application.dto.InventoryRebuildEntry;
import com.reservation.hotel.application.dto.RoomAvailabilitySnapshot;
import com.reservation.hotel.application.port.RoomAvailabilityCache;
import com.reservation.hotel.application.port.RoomAvailabilityQuery;
import com.reservation.hotel.application.port.RoomAvailabilityRebuilder;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link RoomAvailabilityCache} 의 Redis 구현. ADR 0004 의 {@code RoomAvailabilityView}
 * Read Model 스키마를 Redis Hash 로 보관한다.
 *
 * <pre>
 * key:    avail:{hotelId}:{roomTypeId}:{YYYY-MM-DD}
 * fields: available (integer as string), total (integer as string),
 *         updatedAt (ISO-8601 Instant string)
 * TTL:    없음 (ADR 0004 · PRD Q7) — 90일 외 key 는 PR-3.3 일일 배치가 정리
 * </pre>
 *
 * <p>증감은 <strong>Lua 스크립트 단일 실행</strong> 으로 원자성을 보장한다. 첫 분기에
 * {@code EXISTS} 로 key 부재를 감지해 {@code 0} 을 반환 — 이때 호출자는 skip 처리.
 * key 가 존재하면 {@code HINCRBY} 로 available 을 증감하고 {@code updatedAt} 을 갱신.
 * {@code HINCRBY} 는 field 가 없으면 0 에서 시작하는데, key 자체가 없으면 total 이 함께
 * 채워지지 않은 불완전 entry 가 생성되므로 반드시 EXISTS 가 앞서야 한다.
 */
@Component
@RequiredArgsConstructor
public class RedisRoomAvailabilityCache
    implements RoomAvailabilityCache, RoomAvailabilityQuery, RoomAvailabilityRebuilder {

    static final String KEY_PREFIX = "avail:";
    /** Redis Hash field 이름. Lua 스크립트와 후속 조회 API (PR-3.2) 가 공유해야 하는 계약. */
    static final String FIELD_AVAILABLE = "available";
    static final String FIELD_TOTAL = "total";
    static final String FIELD_UPDATED_AT = "updatedAt";

    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ISO_INSTANT;

    /**
     * 1 = 증감 완료, 0 = key 부재로 skip. Lua 내부 모든 명령은 Redis 단일 thread 가 순차
     * 실행 → atomicity 확보. field 이름은 위 상수들과 일관되게 유지해야 하며, {@code static}
     * 블록에서 포맷팅해 스크립트 SHA 가 JVM 생애주기 동안 고정되도록 한다.
     */
    private static final String LUA_ADJUST_TEMPLATE = """
        if redis.call('EXISTS', KEYS[1]) == 0 then
          return 0
        end
        redis.call('HINCRBY', KEYS[1], '%s', ARGV[1])
        redis.call('HSET', KEYS[1], '%s', ARGV[2])
        return 1
        """;

    private static final String LUA_ADJUST =
        String.format(LUA_ADJUST_TEMPLATE, FIELD_AVAILABLE, FIELD_UPDATED_AT);

    private static final RedisScript<Long> ADJUST_SCRIPT =
        new DefaultRedisScript<>(LUA_ADJUST, Long.class);

    private final StringRedisTemplate redisTemplate;

    private static final List<String> READ_FIELDS =
        List.of(FIELD_AVAILABLE, FIELD_TOTAL, FIELD_UPDATED_AT);

    @Override
    public boolean adjustAvailable(HotelId hotelId,
                                   RoomTypeId roomTypeId,
                                   LocalDate stayDate,
                                   int delta,
                                   Instant updatedAt) {
        String key = buildKey(hotelId, roomTypeId, stayDate);
        Long result = redisTemplate.execute(
            ADJUST_SCRIPT,
            Collections.singletonList(key),
            String.valueOf(delta),
            UPDATED_AT_FORMAT.format(updatedAt));
        return result != null && result == 1L;
    }

    /**
     * FR-H-06 — 지정 반개구간의 각 날짜에 대한 HMGET 을 하나의 Redis pipeline 으로 묶어 1 RTT
     * 에 전송한다. 최대 90일 × HMGET 을 순차로 돌리면 프로덕션 RTT 기준 SLO(PRD §6 p99 200ms)
     * 를 초과할 수 있어 파이프라인화를 선택. 부재 key 또는 field 누락은 결과에서 제외한다.
     *
     * <p>Spring Data Redis 의 {@code executePipelined} 는 SessionCallback 내에서 수행된 명령의
     * 결과를 순서대로 담은 {@code List<Object>} 를 반환 — 날짜 인덱스와 결과 인덱스가 1:1 로
     * 대응하므로 재정렬 없이 매핑 가능.
     */
    @Override
    public List<RoomAvailabilitySnapshot> readRange(HotelId hotelId,
                                                    RoomTypeId roomTypeId,
                                                    LocalDate fromInclusive,
                                                    LocalDate toExclusive) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = fromInclusive; d.isBefore(toExclusive); d = d.plusDays(1)) {
            dates.add(d);
        }
        if (dates.isEmpty()) {
            return List.of();
        }

        List<Object> pipelineResults = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (LocalDate d : dates) {
                    operations.opsForHash().multiGet(buildKey(hotelId, roomTypeId, d), READ_FIELDS);
                }
                return null;
            }
        });

        List<RoomAvailabilitySnapshot> snapshots = new ArrayList<>(pipelineResults.size());
        for (int i = 0; i < pipelineResults.size(); i++) {
            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) pipelineResults.get(i);
            if (values == null || values.size() < 3) {
                continue;
            }
            String available = values.get(0);
            String total = values.get(1);
            String updatedAt = values.get(2);
            if (available == null || total == null || updatedAt == null) {
                continue;
            }
            snapshots.add(new RoomAvailabilitySnapshot(
                dates.get(i),
                Integer.parseInt(available),
                Integer.parseInt(total),
                Instant.parse(updatedAt)));
        }
        return snapshots;
    }

    /**
     * FR-H-08 — 재구축 entry 들을 Redis Hash 로 HSET upsert. pipeline 으로 묶어 대량 데이터도
     * 1 RTT 에 전송한다. 기존 key 는 값이 교체되고, 부재였던 key 는 생성된다. Lua 를 쓰지 않아도
     * 원자성이 필요 없다 — 재구축은 SoT 스냅샷을 그대로 덮어쓰는 "최종적으로 정합" 보장이면
     * 충분하고, 개별 HSET 의 중간 상태가 조회에 노출돼도 다음 HSET 으로 곧 정정된다.
     *
     * <p><strong>실패 시 semantics</strong>: pipeline 중 네트워크/서버 오류로
     * {@link DataAccessException} 이 던져지면 일부 entry 만 기록된 부분 쓰기 상태가 된다.
     * 호출자({@link com.reservation.hotel.application.service.AvailabilityRebuildApplicationService})
     * 는 해당 호텔을 failed 로 집계하고 계속 진행하며, 다음 daily tick 이 동일 entry 를
     * 덮어쓰기로 재시도해 수렴 복구한다.
     */
    @Override
    public void upsertAll(List<InventoryRebuildEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (InventoryRebuildEntry e : entries) {
                    String key = buildKey(e.hotelId(), e.roomTypeId(), e.date());
                    operations.opsForHash().putAll(key, java.util.Map.of(
                        FIELD_AVAILABLE, Integer.toString(e.available()),
                        FIELD_TOTAL, Integer.toString(e.total()),
                        FIELD_UPDATED_AT, UPDATED_AT_FORMAT.format(e.updatedAt())));
                }
                return null;
            }
        });
    }

    static String buildKey(HotelId hotelId, RoomTypeId roomTypeId, LocalDate stayDate) {
        return KEY_PREFIX + hotelId.asString() + ':' + roomTypeId.asString() + ':' + stayDate;
    }
}

package com.reservation.hotel.infrastructure.cache;

import com.reservation.hotel.application.port.RoomAvailabilityCache;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

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
public class RedisRoomAvailabilityCache implements RoomAvailabilityCache {

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

    static String buildKey(HotelId hotelId, RoomTypeId roomTypeId, LocalDate stayDate) {
        return KEY_PREFIX + hotelId.asString() + ':' + roomTypeId.asString() + ':' + stayDate;
    }
}

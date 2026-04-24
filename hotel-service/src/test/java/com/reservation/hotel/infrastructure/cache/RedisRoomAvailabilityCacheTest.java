package com.reservation.hotel.infrastructure.cache;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedisRoomAvailabilityCache} 를 실제 Redis 컨테이너 위에서 검증한다.
 *
 * <p>Testcontainers 의 {@link GenericContainer} 로 {@code redis:7.4-alpine} (docker-compose 와 동일
 * 이미지) 을 부팅해 Lua 스크립트 경로까지 실제로 실행한다. Spring Boot 컨텍스트는 띄우지 않고
 * {@link LettuceConnectionFactory} + {@link StringRedisTemplate} 만 수동으로 구성해 slice 부담을 낮춘다.
 *
 * <p>{@code @Testcontainers(disabledWithoutDocker = true)} 로 Docker 미설치 로컬에서는 자동 skip.
 * CI 는 항상 Docker 를 제공하므로 실 실행된다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RedisRoomAvailabilityCache — Lua 스크립트 기반 원자 증감")
class RedisRoomAvailabilityCacheTest {

    private static final HotelId HOTEL_ID =
        HotelId.of(UUID.fromString("01933333-1111-7aaa-9aaa-111122223333"));
    private static final RoomTypeId ROOM_TYPE_ID =
        RoomTypeId.of(UUID.fromString("01933333-2222-7aaa-9aaa-111122223333"));
    private static final LocalDate STAY_DATE = LocalDate.of(2026, 6, 1);
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private RedisRoomAvailabilityCache cache;

    @BeforeAll
    static void initTemplate() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownTemplate() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        cache = new RedisRoomAvailabilityCache(redisTemplate);
    }

    @Test
    @DisplayName("key 부재 → false 반환, key 생성되지 않음")
    void returns_false_when_key_absent() {
        boolean result = cache.adjustAvailable(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, -1, NOW);

        assertThat(result).isFalse();
        String key = RedisRoomAvailabilityCache.buildKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE);
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("key 존재 → available 감소 + updatedAt 갱신 + true 반환")
    void decrements_when_key_exists() {
        // given — key 를 미리 채워놓는다 (실제 PR-3.3 배치가 담당할 역할을 시뮬레이션)
        String key = RedisRoomAvailabilityCache.buildKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE);
        redisTemplate.opsForHash().putAll(key, Map.of(
            "available", "10",
            "total", "10",
            "updatedAt", "2026-05-01T00:00:00Z"));

        // when
        boolean result = cache.adjustAvailable(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, -1, NOW);

        // then
        assertThat(result).isTrue();
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        assertThat(hash).containsEntry("available", "9");
        assertThat(hash).containsEntry("total", "10");
        assertThat(hash).containsEntry("updatedAt", "2026-06-01T10:00:00Z");
    }

    @Test
    @DisplayName("양수 delta — 취소 복원 시 available 증가")
    void increments_when_positive_delta() {
        String key = RedisRoomAvailabilityCache.buildKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE);
        redisTemplate.opsForHash().putAll(key, Map.of(
            "available", "3",
            "total", "10"));

        boolean result = cache.adjustAvailable(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, +1, NOW);

        assertThat(result).isTrue();
        assertThat(redisTemplate.opsForHash().get(key, "available")).isEqualTo("4");
    }

    @Test
    @DisplayName("반복 호출 누적 — 네 번 감소하면 available 이 누적 반영")
    void accumulates_across_invocations() {
        String key = RedisRoomAvailabilityCache.buildKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE);
        redisTemplate.opsForHash().putAll(key, Map.of("available", "10", "total", "10"));

        cache.adjustAvailable(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, -1, NOW);
        cache.adjustAvailable(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, -1, NOW);
        cache.adjustAvailable(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, -1, NOW);
        cache.adjustAvailable(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, -1, NOW);

        assertThat(redisTemplate.opsForHash().get(key, "available")).isEqualTo("6");
    }

    @Test
    @DisplayName("key 형식: avail:{hotelId}:{roomTypeId}:{YYYY-MM-DD}")
    void key_format_documented() {
        String key = RedisRoomAvailabilityCache.buildKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE);

        assertThat(key).isEqualTo(
            "avail:01933333-1111-7aaa-9aaa-111122223333:01933333-2222-7aaa-9aaa-111122223333:2026-06-01");
    }
}

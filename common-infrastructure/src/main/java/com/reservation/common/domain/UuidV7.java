package com.reservation.common.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * UUID v7 생성기 (RFC 9562 §5.7).
 *
 * <p>상위 48 bit 타임스탬프(ms since epoch, big-endian) + version nibble 7 +
 * variant bits + random suffix 로 구성되어 시간 순 정렬이 가능한 UUID 를 생성한다.
 * PRD §11.2 가 {@code reservationId} 를 UUID v7 로 규정하며, 본 프로젝트는 전
 * Aggregate ID (Hotel · Room · Rate · Guest · Reservation · Billing) 에 동일
 * 규약을 적용한다.
 *
 * <p>JDK 21 의 {@link UUID} 는 v1~v5 만 표준 지원하므로 직접 bit 조작.
 * 생성기는 테스트 주입 가능하도록 {@link Clock} 을 파라미터로 받는 오버로드를 제공.
 */
public final class UuidV7 {

    // JVM 전역 공유. SecureRandom 은 thread-safe 이지만 내부 구현에 따라 synchronized 가
    // 존재하므로 초당 수천 ID 이상의 부하 시 contention hotspot 이 될 수 있다. PRD §6
    // 부하 테스트 시점에 ThreadLocal<SecureRandom> 전환을 재평가한다.
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    /** 시스템 UTC 시각 기준 UUID v7 생성. */
    public static UUID create() {
        return create(Clock.systemUTC());
    }

    /**
     * 주어진 {@link Clock} 기준 UUID v7 생성 (테스트 fixed clock 주입 가능).
     *
     * @throws NullPointerException clock 이 {@code null} 일 때
     */
    public static UUID create(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        long timestampMillis = Instant.now(clock).toEpochMilli();

        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        // bytes[0..5] = 48-bit big-endian timestamp (millisecond)
        bytes[0] = (byte) ((timestampMillis >>> 40) & 0xFF);
        bytes[1] = (byte) ((timestampMillis >>> 32) & 0xFF);
        bytes[2] = (byte) ((timestampMillis >>> 24) & 0xFF);
        bytes[3] = (byte) ((timestampMillis >>> 16) & 0xFF);
        bytes[4] = (byte) ((timestampMillis >>> 8) & 0xFF);
        bytes[5] = (byte) (timestampMillis & 0xFF);

        // bytes[6] high nibble = 0b0111 (version 7), low nibble = random
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        // bytes[8] top 2 bits = 0b10 (RFC 4122 variant), rest = random
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        long msb = 0L;
        long lsb = 0L;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFFL);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFFL);
        }
        return new UUID(msb, lsb);
    }
}

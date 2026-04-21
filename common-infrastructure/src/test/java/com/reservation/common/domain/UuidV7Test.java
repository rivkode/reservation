package com.reservation.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UuidV7Test {

    @Test
    @DisplayName("생성된 UUID 는 version=7, variant=RFC 4122 (2) 비트 세팅을 가진다")
    void generatedUuidHasVersion7AndRfc4122Variant() {
        UUID uuid = UuidV7.create();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("시간 순으로 생성된 UUID 는 lexicographic 정렬이 발생 순서와 일치한다")
    void uuidsAreSortableByCreationTime() {
        UUID earlier = UuidV7.create(Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC));
        UUID later = UuidV7.create(Clock.fixed(Instant.parse("2026-04-22T11:00:00Z"), ZoneOffset.UTC));

        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    @DisplayName("상위 48 bit 에 주어진 timestamp (ms) 가 big-endian 으로 들어간다")
    void upperBitsContainTimestamp() {
        Instant fixed = Instant.parse("2026-04-22T10:00:00Z");
        UUID uuid = UuidV7.create(Clock.fixed(fixed, ZoneOffset.UTC));

        long msb = uuid.getMostSignificantBits();
        long tsFromUuid = (msb >>> 16) & 0xFFFFFFFFFFFFL;

        assertThat(tsFromUuid).isEqualTo(fixed.toEpochMilli());
    }

    @Test
    @DisplayName("연속 호출 시 중복 UUID 가 생성되지 않는다 (same-ms 의 random suffix 고유성)")
    void consecutiveCallsProduceDistinctUuids() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC);

        UUID first = UuidV7.create(fixedClock);
        UUID second = UuidV7.create(fixedClock);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("create() 무인자는 시스템 시계를 사용해 현재 시각으로 UUID 를 만든다")
    void createUsesSystemClockByDefault() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.create();
        long after = System.currentTimeMillis();

        long ts = (uuid.getMostSignificantBits() >>> 16) & 0xFFFFFFFFFFFFL;

        assertThat(ts).isBetween(before, after);
    }

    @Test
    @DisplayName("create(null) 은 NullPointerException 으로 빠르게 실패한다")
    void createRejectsNullClock() {
        assertThatThrownBy(() -> UuidV7.create(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("clock");
    }
}

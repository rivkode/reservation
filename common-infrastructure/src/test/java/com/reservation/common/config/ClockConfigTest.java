package com.reservation.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    @DisplayName("systemClock 빈은 UTC 시간대를 기준으로 한 Clock 을 반환한다")
    void systemClockIsUtc() {
        ClockConfig config = new ClockConfig();

        Clock clock = config.systemClock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}

package com.reservation.guest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class GuestServiceApplicationTests {

    @Autowired
    private Clock clock;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("common-infrastructure 의 공용 Clock 빈이 auto-configuration 으로 등록된다")
    void commonClockBeanIsRegistered() {
        assertThat(clock).isNotNull();
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}

package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("StayPeriod VO")
class StayPeriodTest {

    private static final LocalDate JUN_1 = LocalDate.parse("2026-06-01");
    private static final LocalDate JUN_3 = LocalDate.parse("2026-06-03");

    @Test
    @DisplayName("정상 기간: nights() 는 일자 차이, stayDates() 는 [checkIn, checkOut) 반개구간")
    void nightsAndStayDates() {
        StayPeriod period = new StayPeriod(JUN_1, JUN_3);

        assertThat(period.nights()).isEqualTo(2);
        assertThat(period.stayDates())
            .containsExactly(JUN_1, JUN_1.plusDays(1));
    }

    @Test
    @DisplayName("1박 (checkOut = checkIn + 1) 은 stayDates 가 정확히 1 일")
    void oneNight() {
        StayPeriod period = new StayPeriod(JUN_1, JUN_1.plusDays(1));

        assertThat(period.nights()).isEqualTo(1);
        assertThat(period.stayDates()).containsExactly(JUN_1);
    }

    @Test
    @DisplayName("checkOut 이 checkIn 과 같으면 IllegalArgument — 0박 예약 차단")
    void rejectsZeroNights() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StayPeriod(JUN_1, JUN_1))
            .withMessageContaining("checkOut");
    }

    @Test
    @DisplayName("checkOut 이 checkIn 보다 이전이면 IllegalArgument")
    void rejectsReversed() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new StayPeriod(JUN_3, JUN_1));
    }

    @Test
    @DisplayName("null 거부")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> new StayPeriod(null, JUN_3));
        assertThatNullPointerException().isThrownBy(() -> new StayPeriod(JUN_1, null));
    }
}

package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("NumberOfGuests VO")
class NumberOfGuestsTest {

    @Test
    @DisplayName("최소 1 인 이상은 정상 생성")
    void allowsAtLeastOne() {
        assertThat(NumberOfGuests.of(1).value()).isEqualTo(1);
        assertThat(NumberOfGuests.of(4).value()).isEqualTo(4);
    }

    @Test
    @DisplayName("0 이하는 IllegalArgument")
    void rejectsZeroOrNegative() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> NumberOfGuests.of(0));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> NumberOfGuests.of(-1));
    }
}

package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("CancellationOutcome VO")
class CancellationOutcomeTest {

    @Test
    @DisplayName("refundRate 가 [0.00, 1.00] 범위 밖이면 IllegalArgument")
    void rejectsRefundRateOutOfBounds() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new CancellationOutcome(new BigDecimal("-0.01"), "X"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new CancellationOutcome(new BigDecimal("1.01"), "X"));
    }

    @Test
    @DisplayName("policyName 이 빈 문자열이면 IllegalArgument")
    void rejectsBlankPolicyName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new CancellationOutcome(BigDecimal.ZERO, " "));
    }

    @Test
    @DisplayName("필수 인자 null 은 NPE")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() ->
            new CancellationOutcome(null, "X"));
        assertThatNullPointerException().isThrownBy(() ->
            new CancellationOutcome(BigDecimal.ZERO, null));
    }
}

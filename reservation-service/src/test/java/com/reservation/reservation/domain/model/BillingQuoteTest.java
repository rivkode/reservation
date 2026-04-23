package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("BillingQuote VO")
class BillingQuoteTest {

    @Test
    @DisplayName("정상 필드는 그대로 보존")
    void retainsValues() {
        Instant now = Instant.parse("2026-04-23T01:00:00Z");
        BillingQuote quote = new BillingQuote(Money.of(300_000L, "KRW"), now);

        assertThat(quote.total()).isEqualTo(Money.of(300_000L, "KRW"));
        assertThat(quote.quotedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("null total / quotedAt 은 NPE")
    void rejectsNulls() {
        assertThatNullPointerException()
            .isThrownBy(() -> new BillingQuote(null, Instant.now()));
        assertThatNullPointerException()
            .isThrownBy(() -> new BillingQuote(Money.zero("KRW"), null));
    }
}

package com.reservation.reservation.domain.model;

import com.reservation.reservation.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Money VO")
class MoneyTest {

    @Test
    @DisplayName("zero · of 팩토리는 동일한 amount=0/지정 amount 의 인스턴스를 만든다")
    void factories() {
        assertThat(Money.zero("KRW").amount()).isZero();
        assertThat(Money.of(150_000L, "KRW")).isEqualTo(new Money(150_000L, "KRW"));
    }

    @Test
    @DisplayName("음수 amount 는 IllegalArgument")
    void rejectsNegativeAmount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new Money(-1L, "KRW"));
    }

    @Test
    @DisplayName("null currency 는 NPE")
    void rejectsNullCurrency() {
        assertThatNullPointerException().isThrownBy(() -> new Money(0L, null));
    }

    @Test
    @DisplayName("blank currency 는 IllegalArgument — 직렬화/응답 누락 방어")
    void rejectsBlankCurrency() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new Money(0L, ""));
    }

    @Test
    @DisplayName("동일 통화 add 는 합산")
    void addSameCurrency() {
        Money a = Money.of(100_000L, "KRW");
        Money b = Money.of(50_000L, "KRW");

        assertThat(a.add(b)).isEqualTo(Money.of(150_000L, "KRW"));
    }

    @Test
    @DisplayName("다른 통화 add 는 CurrencyMismatchException — N일치 견적 합산 불변식")
    void addDifferentCurrencyThrows() {
        Money krw = Money.of(100_000L, "KRW");
        Money jpy = Money.of(10_000L, "JPY");

        assertThatExceptionOfType(CurrencyMismatchException.class)
            .isThrownBy(() -> krw.add(jpy))
            .withMessageContaining("KRW")
            .withMessageContaining("JPY");
    }
}

package com.reservation.reservation.domain.model;

import com.reservation.reservation.domain.exception.CurrencyMismatchException;

import java.util.Objects;

/**
 * 통화 단위가 부여된 금액 VO.
 *
 * <p>{@code amount} 는 통화 최소 단위 정수 (KRW 는 원, JPY 는 엔). PRD §8 의 응답 스펙
 * 과 일치한다. 부동소수 누적 오차를 피하고 rate-service 의 proto 정의 ({@code int64
 * amount}) 와 동일 표현을 유지하기 위해 BigDecimal 이 아닌 {@code long} 을 사용한다.
 *
 * <p>{@link #add(Money)} 는 두 통화가 다르면 {@link CurrencyMismatchException} 을
 * 던진다 — N 일치 견적이 모두 동일 통화여야 한다는 견적 합산 불변식을 VO 가 직접 방어.
 *
 * <p>본 VO 는 음의 금액을 허용하지 않는다. 환불 표현이 필요해지면 (PR-2.3 위약금 정책)
 * 별도 {@code Refund} VO 로 분리하거나 본 VO 의 정책을 재검토할 것.
 */
public record Money(long amount, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (amount < 0) {
            throw new IllegalArgumentException("Money amount must be non-negative, was " + amount);
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public static Money of(long amount, String currency) {
        return new Money(amount, currency);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other");
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(this.currency, other.currency);
        }
        return new Money(this.amount + other.amount, this.currency);
    }
}

package com.reservation.rate.domain.model;

import java.util.Currency;
import java.util.Objects;

/**
 * 요금 금액과 통화를 함께 표현하는 값 객체.
 *
 * <p>{@code amount} 는 통화의 최소 단위 정수 (minor units) 로 보관한다 — 예: KRW 는
 * 원 단위 정수, USD 는 센트 단위 정수. {@code contracts/proto/rate.proto} 의
 * {@code int64 amount} · {@link com.reservation.contracts.event.rate.RoomTypeRateChangedEvent#amount}
 * 와 동일 표현이므로 직렬화 시 변환이 불필요하다.
 *
 * <p>불변식:
 * <ul>
 *   <li>{@code amount >= 0} — 음수 요금은 도메인상 무의미</li>
 *   <li>{@code currency} 는 ISO-4217 유효 통화 ({@link Currency#getInstance(String)} 위임)</li>
 * </ul>
 */
public record Money(long amount, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amount);
        }
    }

    /**
     * ISO-4217 통화 코드 문자열로부터 생성한다. 무효 코드는
     * {@link Currency#getInstance(String)} 가 {@link IllegalArgumentException} 으로 거부한다.
     */
    public static Money of(long amount, String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode");
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }
}

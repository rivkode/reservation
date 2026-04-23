package com.reservation.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 예약 시점에 합의된 청구 견적 VO. {@link Reservation} 의 불변 필드로 보유되어
 * 이후 rate 가 변경되어도 본 예약의 청구액은 본 VO 가 보유한 값으로 고정된다 —
 * "예약은 시점의 합의" 라는 도메인 의미를 코드에 드러낸다 (ddd-architect H1, ADR 0003).
 *
 * <p>{@code quotedAt} 은 rate-service gRPC 호출 직후 reservation-service 가 기록한
 * 단조 시각이며, 후속 BillingCreated 이벤트와의 정합 검증에 사용될 수 있다 (PR-2.3
 * 이후 Saga 보강 시).
 */
public record BillingQuote(Money total, Instant quotedAt) {

    public BillingQuote {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(quotedAt, "quotedAt");
    }
}

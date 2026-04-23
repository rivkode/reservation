package com.reservation.reservation.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 예약 취소가 발생했을 때 {@link Reservation} 이 보유하는 취소 정보 묶음.
 *
 * <p>{@code (cancelledAt, reason, outcome)} 세 값은 항상 함께 결정되고 함께 다닌다 —
 * 취소가 일어나지 않은 예약 (CONFIRMED) 은 세 값이 모두 의미가 없고, 취소가 일어난
 * 예약은 세 값이 모두 채워진다. 따라서 {@link Reservation} 은 본 VO 한 덩어리로 nullable
 * 필드를 보유하고 {@code (status == CANCELLED) ↔ (cancellation != null)} 불변식을 유지
 * 한다 (ddd-architect Critical-1).
 *
 * <p>JpaEntity 측에서는 4개 평탄 컬럼 ({@code cancelled_at} · {@code cancellation_reason}
 * · {@code refund_rate} · {@code cancellation_policy_name}) 으로 펼쳐 매핑한다 — Aggregate
 * 표면은 단일 VO 만 노출되고, 영속화는 Mapper 가 흡수한다.
 */
public record Cancellation(Instant cancelledAt, CancellationReason reason, CancellationOutcome outcome) {

    public Cancellation {
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(outcome, "outcome");
    }
}

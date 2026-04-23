package com.reservation.reservation.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * {@link CancellationPolicy#decide} 가 반환하는 정책 결정 결과.
 *
 * <p>{@code refundRate} 는 {@code [0.00, 1.00]} 의 환불 비율 ({@code BigDecimal} scale 2).
 * {@code policyName} 은 결정을 내린 정책 구현체의 식별자 (예: {@code "TWENTY_FOUR_HOUR"})
 * 로, 향후 결제 PRD 가 도입되었을 때 "어떤 정책으로 결정된 환불률인지" 를 감사할 수 있게
 * 한다 — ddd-architect Critical-1 의 "dead-data 의미 약화" 처방.
 *
 * <p>실제 환불 처리 (결제 시스템과 연동) 는 본 PR 범위 외 (PRD §13 Q3 — "상태 기록만").
 */
public record CancellationOutcome(BigDecimal refundRate, String policyName) {

    private static final BigDecimal MIN_RATE = BigDecimal.ZERO;
    private static final BigDecimal MAX_RATE = BigDecimal.ONE;

    public CancellationOutcome {
        Objects.requireNonNull(refundRate, "refundRate");
        Objects.requireNonNull(policyName, "policyName");
        if (refundRate.compareTo(MIN_RATE) < 0 || refundRate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException(
                "refundRate must be within [0.00, 1.00], was " + refundRate);
        }
        if (policyName.isBlank()) {
            throw new IllegalArgumentException("policyName must not be blank");
        }
    }
}

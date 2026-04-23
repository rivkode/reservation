package com.reservation.reservation.domain.model;

/**
 * 예약 취소가 어떤 경로로 발생했는지를 표현하는 도메인 enum.
 *
 * <p>두 가지 경로:
 * <ul>
 *   <li>{@link #USER_REQUEST} — 사용자가 명시적으로 취소 API 를 호출한 경우.</li>
 *   <li>{@link #BILLING_FAILED} — Saga 보상 트랜잭션. rate-service 의
 *       {@code BillingCreationFailedEvent} 를 reservation-service 가 구독해 자동 취소
 *       (ADR 0003 §보상 경로).</li>
 * </ul>
 *
 * <p>두 경로의 도메인 차이는 {@link CancellationPolicy#decide} 의 입력으로 reason 이
 * 흘러가 정책이 차등 결정할 수 있게 하는 데 있다 — 본 PR 시점의
 * {@code TwentyFourHourCancellationPolicy} 는 reason 을 무시하지만, 향후 결제 PRD 가
 * 도입되면 "BILLING_FAILED 는 자동 100% 환불 + ops 알림" 같은 분기를 정책 객체 안에서
 * 표현하게 된다 (ddd-architect High-2).
 */
public enum CancellationReason {

    USER_REQUEST,
    BILLING_FAILED
}

package com.reservation.reservation.domain.model;

/**
 * Reservation Aggregate 의 상태 전이 enum.
 *
 * <p>PR-2.2 범위에서는 {@link #CONFIRMED} 단일 진입 상태만 사용한다. 본 PR 의 예약
 * 생성 흐름은 reservation-service 의 SoT 재검증이 끝난 직후 즉시 CONFIRMED 로 확정되며
 * 별도의 PENDING 단계를 두지 않는다 — Plan §2 결정.
 *
 * <p>{@link #CANCELLED} 는 PR-2.3 (취소 API · BillingCreationFailed 보상 트랜잭션) 에서
 * 추가될 예정이지만, 미리 enum 에 도입하면 본 PR 의 ExceptionHandler · 영속화 매핑이
 * 후속 PR 에서 다시 마이그레이션될 필요가 없다 — 자료형 설계의 forward-compatibility.
 */
public enum ReservationStatus {

    CONFIRMED,
    CANCELLED
}

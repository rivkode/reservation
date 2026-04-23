package com.reservation.reservation.application.dto;

import com.reservation.reservation.domain.model.Cancellation;
import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 예약 취소 Use Case 결과. {@link Cancellation} 의 시각·환불률·정책명을 펼쳐 노출하며
 * Presentation 이 그대로 응답으로 변환한다.
 */
public record CancelReservationResult(
    String reservationId,
    ReservationStatus status,
    Instant cancelledAt,
    BigDecimal refundRate,
    String cancellationPolicyName
) {

    /**
     * CANCELLED 상태가 아닌 {@link Reservation} 에 대해 호출되면 {@link IllegalStateException}
     * 를 던진다 — Application Service 의 cancel 흐름 끝에서만 호출되므로 본 케이스는
     * 도달 시점이 곧 production 버그를 의미한다 (Aggregate 불변식 위반). 예외는
     * {@code ReservationExceptionHandler} 의 generic {@code RuntimeException} 핸들러가
     * 500 으로 매핑하며, 클라이언트에는 일반 internal-error 메시지가 노출된다.
     */
    public static CancelReservationResult of(Reservation reservation) {
        Cancellation cancellation = reservation.cancellation().orElseThrow(() ->
            new IllegalStateException(
                "CancelReservationResult requires CANCELLED reservation: id="
                    + reservation.id().asString()));
        return new CancelReservationResult(
            reservation.id().asString(),
            reservation.status(),
            cancellation.cancelledAt(),
            cancellation.outcome().refundRate(),
            cancellation.outcome().policyName()
        );
    }
}

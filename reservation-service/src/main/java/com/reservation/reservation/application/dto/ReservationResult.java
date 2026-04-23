package com.reservation.reservation.application.dto;

import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationStatus;

/**
 * 예약 Use Case 결과. Presentation 계층이 {@code CommonResponse<ReservationResponse>}
 * 로 감싸 응답한다.
 */
public record ReservationResult(
    String reservationId,
    ReservationStatus status,
    long totalAmount,
    String currency
) {

    public static ReservationResult of(Reservation reservation) {
        return new ReservationResult(
            reservation.id().asString(),
            reservation.status(),
            reservation.quote().total().amount(),
            reservation.quote().total().currency()
        );
    }
}

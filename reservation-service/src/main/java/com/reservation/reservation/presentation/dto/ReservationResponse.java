package com.reservation.reservation.presentation.dto;

import com.reservation.reservation.application.dto.ReservationResult;
import com.reservation.reservation.domain.model.ReservationStatus;

/**
 * 예약 생성 REST 응답 DTO. PRD §8.1 의 응답 스키마와 정합.
 *
 * <p>{@code totalAmount}/{@code currency} 는 예약 시점의 견적 스냅샷이며 이후 rate
 * 변경에도 본 응답값이 청구액으로 고정된다 (BillingQuote VO).
 */
public record ReservationResponse(
    String reservationId,
    ReservationStatus status,
    long totalAmount,
    String currency
) {

    public static ReservationResponse of(ReservationResult result) {
        return new ReservationResponse(
            result.reservationId(),
            result.status(),
            result.totalAmount(),
            result.currency()
        );
    }
}

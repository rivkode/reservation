package com.reservation.reservation.domain.exception;

import com.reservation.reservation.domain.model.ReservationId;

/**
 * 이미 {@code CANCELLED} 상태인 예약에 대해 다시 취소를 시도했을 때 {@code Reservation.cancel}
 * 이 던지는 도메인 예외.
 *
 * <p>두 경로의 처리 차이:
 * <ul>
 *   <li>사용자 경로 — Presentation 에서 {@code 409 RESERVATION_ALREADY_CANCELLED} 로 매핑.
 *       사용자에게 "이미 취소된 예약" 임을 명확히 알린다.</li>
 *   <li>Saga 보상 경로 — Application Service 가 본 예외를 catch 해서 warn 로그 + processed
 *       기록만 남기고 흡수한다. {@code BillingCreationFailedEvent} 가 재전송될 수 있고
 *       이미 사용자가 동시에 취소했을 가능성도 있어 noop 이 안전.</li>
 * </ul>
 *
 * <p>도메인 객체 ({@link com.reservation.reservation.domain.model.Reservation}) 는 두 경로를
 * 모르고 일관되게 예외를 던지며, 분기는 Application Service 가 책임진다.
 */
public class ReservationAlreadyCancelledException extends RuntimeException {

    private final ReservationId reservationId;

    public ReservationAlreadyCancelledException(ReservationId reservationId) {
        super("Reservation already cancelled: " + reservationId.asString());
        this.reservationId = reservationId;
    }

    public ReservationId reservationId() {
        return reservationId;
    }
}

package com.reservation.reservation.domain.exception;

import com.reservation.reservation.domain.model.ReservationId;

/**
 * 주어진 {@link ReservationId} 의 예약이 존재하지 않을 때 발생.
 *
 * <p>발생 경로:
 * <ul>
 *   <li>사용자 취소 API — 클라이언트가 잘못된 reservationId 를 넘김 → 404 매핑.</li>
 *   <li>Saga 보상 경로 — {@code BillingCreationFailedEvent} 의 reservationId 가 본 서비스에
 *       없음 (이벤트 순서 역전 / 다른 reservation-service 인스턴스 데이터). 이 경우
 *       Application Service 가 warn 로그 + processed 기록만 남기고 흡수한다.</li>
 * </ul>
 */
public class ReservationNotFoundException extends RuntimeException {

    private final ReservationId reservationId;

    public ReservationNotFoundException(ReservationId reservationId) {
        super("Reservation not found: " + reservationId.asString());
        this.reservationId = reservationId;
    }

    public ReservationId reservationId() {
        return reservationId;
    }
}

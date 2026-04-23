package com.reservation.reservation.presentation.dto;

import com.reservation.reservation.application.dto.CreateReservationCommand;

import java.time.LocalDate;

/**
 * 예약 생성 REST 요청 DTO. PRD §8.1 의 스키마와 정합.
 *
 * <p>입력 검증은 도메인 VO ({@link com.reservation.reservation.domain.model.GuestId} ·
 * {@link com.reservation.reservation.domain.model.StayPeriod} ·
 * {@link com.reservation.reservation.domain.model.NumberOfGuests} 등) 가 수행하고
 * {@code IllegalArgumentException} → 400 으로 매핑된다 (CLAUDE.md "presentation 의존성"
 * 절 — jakarta.validation 도입은 별도 PR).
 */
public record CreateReservationRequest(
    String hotelId,
    String roomTypeId,
    String guestId,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int numberOfGuests
) {

    public CreateReservationCommand toCommand() {
        return new CreateReservationCommand(
            hotelId, roomTypeId, guestId,
            checkInDate, checkOutDate, numberOfGuests);
    }
}

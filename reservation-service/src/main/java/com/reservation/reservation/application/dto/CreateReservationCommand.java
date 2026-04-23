package com.reservation.reservation.application.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 예약 생성 Use Case 입력. Presentation 계층의 {@code CreateReservationRequest} 가
 * 본 record 로 변환된다.
 *
 * <p>id 들은 String 타입이다 — Application Service 가 Domain VO 로 변환하면서 형식
 * 검증을 함께 수행한다 (잘못된 UUID 는 {@link IllegalArgumentException} → 400).
 */
public record CreateReservationCommand(
    String hotelId,
    String roomTypeId,
    String guestId,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int numberOfGuests
) {

    public CreateReservationCommand {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        Objects.requireNonNull(guestId, "guestId");
        Objects.requireNonNull(checkInDate, "checkInDate");
        Objects.requireNonNull(checkOutDate, "checkOutDate");
    }
}

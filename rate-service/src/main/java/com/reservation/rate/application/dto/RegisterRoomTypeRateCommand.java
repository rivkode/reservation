package com.reservation.rate.application.dto;

import java.time.LocalDate;

/**
 * 요금 등록 Command. 원시 타입으로 선언해 Presentation → Application 진입부에서 VO 변환.
 */
public record RegisterRoomTypeRateCommand(
    String hotelId,
    String roomTypeId,
    LocalDate date,
    long amount,
    String currency
) {
}

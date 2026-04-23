package com.reservation.rate.presentation.dto;

import com.reservation.rate.application.dto.RoomTypeRateResult;

import java.time.LocalDate;

public record RoomTypeRateResponse(
    String id,
    String hotelId,
    String roomTypeId,
    LocalDate date,
    long amount,
    String currency
) {

    public static RoomTypeRateResponse of(RoomTypeRateResult result) {
        return new RoomTypeRateResponse(
            result.id(), result.hotelId(), result.roomTypeId(),
            result.date(), result.amount(), result.currency());
    }
}

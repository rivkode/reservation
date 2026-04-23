package com.reservation.rate.application.dto;

import com.reservation.rate.domain.model.RoomTypeRate;

import java.time.LocalDate;

public record RoomTypeRateResult(
    String id,
    String hotelId,
    String roomTypeId,
    LocalDate date,
    long amount,
    String currency
) {

    public static RoomTypeRateResult of(RoomTypeRate rate) {
        return new RoomTypeRateResult(
            rate.id().asString(),
            rate.hotelId().asString(),
            rate.roomTypeId().asString(),
            rate.date(),
            rate.money().amount(),
            rate.money().currencyCode()
        );
    }
}

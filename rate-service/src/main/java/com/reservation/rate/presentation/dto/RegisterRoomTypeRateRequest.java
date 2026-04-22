package com.reservation.rate.presentation.dto;

import com.reservation.rate.application.dto.RegisterRoomTypeRateCommand;

import java.time.LocalDate;

public record RegisterRoomTypeRateRequest(
    String hotelId,
    String roomTypeId,
    LocalDate date,
    long amount,
    String currency
) {

    public RegisterRoomTypeRateCommand toCommand() {
        return new RegisterRoomTypeRateCommand(hotelId, roomTypeId, date, amount, currency);
    }
}

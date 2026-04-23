package com.reservation.rate.presentation.dto;

import com.reservation.rate.application.dto.ChangeRoomTypeRateCommand;

public record ChangeRoomTypeRateRequest(
    long amount,
    String currency
) {

    public ChangeRoomTypeRateCommand toCommand(String rateId) {
        return new ChangeRoomTypeRateCommand(rateId, amount, currency);
    }
}

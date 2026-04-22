package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.RegisterRoomTypeCommand;

public record RegisterRoomTypeRequest(
    String hotelId,
    String name,
    int maxOccupancy
) {

    public RegisterRoomTypeCommand toCommand() {
        return new RegisterRoomTypeCommand(hotelId, name, maxOccupancy);
    }
}

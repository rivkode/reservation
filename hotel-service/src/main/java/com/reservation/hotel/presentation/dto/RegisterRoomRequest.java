package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.RegisterRoomCommand;

public record RegisterRoomRequest(
    String hotelId,
    String roomTypeId,
    int floor,
    String number
) {

    public RegisterRoomCommand toCommand() {
        return new RegisterRoomCommand(hotelId, roomTypeId, floor, number);
    }
}

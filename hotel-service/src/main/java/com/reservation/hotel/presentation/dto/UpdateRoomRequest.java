package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.UpdateRoomCommand;

public record UpdateRoomRequest(
    String roomTypeId
) {

    public UpdateRoomCommand toCommand(String roomId) {
        return new UpdateRoomCommand(roomId, roomTypeId);
    }
}

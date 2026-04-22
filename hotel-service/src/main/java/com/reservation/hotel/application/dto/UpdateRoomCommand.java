package com.reservation.hotel.application.dto;

public record UpdateRoomCommand(
    String roomId,
    String roomTypeId
) {
}

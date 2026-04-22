package com.reservation.hotel.application.dto;

public record RegisterRoomTypeCommand(
    String hotelId,
    String name,
    int maxOccupancy
) {
}

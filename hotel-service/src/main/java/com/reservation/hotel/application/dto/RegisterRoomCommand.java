package com.reservation.hotel.application.dto;

public record RegisterRoomCommand(
    String hotelId,
    String roomTypeId,
    int floor,
    String number
) {
}

package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.RoomResult;

public record RoomResponse(
    String id,
    String hotelId,
    String roomTypeId,
    int floor,
    String number,
    String status
) {

    public static RoomResponse of(RoomResult result) {
        return new RoomResponse(result.id(), result.hotelId(), result.roomTypeId(),
            result.floor(), result.number(), result.status());
    }
}

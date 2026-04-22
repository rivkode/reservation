package com.reservation.hotel.application.dto;

import com.reservation.hotel.domain.model.Room;

public record RoomResult(
    String id,
    String hotelId,
    String roomTypeId,
    int floor,
    String number,
    String status
) {

    public static RoomResult of(Room room) {
        return new RoomResult(
            room.id().asString(),
            room.hotelId().asString(),
            room.roomTypeId().asString(),
            room.floor().value(),
            room.number().value(),
            room.status().name()
        );
    }
}

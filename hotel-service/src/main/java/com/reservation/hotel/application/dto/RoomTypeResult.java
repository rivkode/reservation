package com.reservation.hotel.application.dto;

import com.reservation.hotel.domain.model.RoomType;

public record RoomTypeResult(
    String id,
    String hotelId,
    String name,
    int maxOccupancy
) {

    public static RoomTypeResult of(RoomType roomType) {
        return new RoomTypeResult(
            roomType.id().asString(),
            roomType.hotelId().asString(),
            roomType.name().value(),
            roomType.maxOccupancy().value()
        );
    }
}

package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.RoomTypeResult;

public record RoomTypeResponse(
    String id,
    String hotelId,
    String name,
    int maxOccupancy
) {

    public static RoomTypeResponse of(RoomTypeResult result) {
        return new RoomTypeResponse(result.id(), result.hotelId(), result.name(), result.maxOccupancy());
    }
}

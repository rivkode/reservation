package com.reservation.hotel.domain.exception;

import com.reservation.hotel.domain.model.RoomTypeId;

public class RoomTypeNotFoundException extends RuntimeException {

    private final RoomTypeId roomTypeId;

    public RoomTypeNotFoundException(RoomTypeId roomTypeId) {
        super("RoomType not found: " + roomTypeId.asString());
        this.roomTypeId = roomTypeId;
    }

    public RoomTypeId roomTypeId() {
        return roomTypeId;
    }
}

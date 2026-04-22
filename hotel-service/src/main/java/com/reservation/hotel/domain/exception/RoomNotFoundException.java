package com.reservation.hotel.domain.exception;

import com.reservation.hotel.domain.model.RoomId;

public class RoomNotFoundException extends RuntimeException {

    private final RoomId roomId;

    public RoomNotFoundException(RoomId roomId) {
        super("Room not found: " + roomId.asString());
        this.roomId = roomId;
    }

    public RoomId roomId() {
        return roomId;
    }
}

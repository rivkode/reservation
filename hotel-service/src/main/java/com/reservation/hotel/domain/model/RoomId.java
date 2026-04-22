package com.reservation.hotel.domain.model;

import com.reservation.common.domain.DomainId;
import com.reservation.common.domain.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record RoomId(UUID value) implements DomainId {

    public RoomId {
        Objects.requireNonNull(value, "value");
    }

    public static RoomId newId() {
        return new RoomId(UuidV7.create());
    }

    public static RoomId of(UUID value) {
        return new RoomId(value);
    }

    public static RoomId of(String value) {
        Objects.requireNonNull(value, "value");
        return new RoomId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

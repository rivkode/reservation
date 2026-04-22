package com.reservation.hotel.domain.model;

import com.reservation.common.domain.DomainId;
import com.reservation.common.domain.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record RoomTypeId(UUID value) implements DomainId {

    public RoomTypeId {
        Objects.requireNonNull(value, "value");
    }

    public static RoomTypeId newId() {
        return new RoomTypeId(UuidV7.create());
    }

    public static RoomTypeId of(UUID value) {
        return new RoomTypeId(value);
    }

    public static RoomTypeId of(String value) {
        Objects.requireNonNull(value, "value");
        return new RoomTypeId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

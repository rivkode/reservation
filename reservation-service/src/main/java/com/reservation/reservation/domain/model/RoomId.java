package com.reservation.reservation.domain.model;

import com.reservation.common.domain.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * hotel-service 가 발급한 개별 Room 식별자. {@link RoomAssignment} Aggregate 의
 * 식별자 역할만 수행하며, Inventory 집계는 {@link RoomTypeId} 단위이므로 Inventory
 * 키에는 포함되지 않는다.
 */
public record RoomId(UUID value) implements DomainId {

    public RoomId {
        Objects.requireNonNull(value, "value");
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

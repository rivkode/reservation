package com.reservation.reservation.domain.model;

import com.reservation.common.domain.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * hotel-service 가 발급한 RoomType 식별자. reservation-service 는 RoomTypeInventory 의
 * 자연 키 일부로만 사용하고, RoomType 의 수용 인원/요금 같은 속성은 보유하지 않는다
 * (Bounded Context 분리).
 */
public record RoomTypeId(UUID value) implements DomainId {

    public RoomTypeId {
        Objects.requireNonNull(value, "value");
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

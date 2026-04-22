package com.reservation.hotel.domain.model;

import com.reservation.common.domain.DomainId;
import com.reservation.common.domain.UuidV7;

import java.util.Objects;
import java.util.UUID;

/**
 * Hotel Aggregate 식별자. 내부적으로 UUID v7 값을 보유한다.
 *
 * <p>생성은 {@link #newId()} 로, 문자열 복원은 {@link #of(String)} 로 수행한다.
 * null 값은 허용되지 않으며 compact constructor 에서 즉시 거부한다.
 */
public record HotelId(UUID value) implements DomainId {

    public HotelId {
        Objects.requireNonNull(value, "value");
    }

    public static HotelId newId() {
        return new HotelId(UuidV7.create());
    }

    public static HotelId of(UUID value) {
        return new HotelId(value);
    }

    public static HotelId of(String value) {
        Objects.requireNonNull(value, "value");
        return new HotelId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

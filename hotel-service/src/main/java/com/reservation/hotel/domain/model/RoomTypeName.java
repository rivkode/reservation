package com.reservation.hotel.domain.model;

import java.util.Objects;

/**
 * RoomType 이름 VO. {@code (hotelId, name)} 조합이 UNIQUE (DB 레벨) 이고,
 * 이 제약은 Hotel Aggregate 내 Application Service 에서 선제 검증 후 DB 레벨에서 재차
 * 방어한다.
 */
public record RoomTypeName(String value) {

    public static final int MAX_LENGTH = 100;

    public RoomTypeName {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("RoomTypeName must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "RoomTypeName must be at most " + MAX_LENGTH + " characters, was " + trimmed.length());
        }
        value = trimmed;
    }
}

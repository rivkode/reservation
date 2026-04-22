package com.reservation.hotel.domain.model;

import java.util.Objects;

/**
 * 객실 번호 VO. 자릿수 / 문자 조합(예: "101", "A-12") 을 그대로 허용하되 공백을
 * 제거해 DB UNIQUE 제약의 동치성 기준을 명확히 한다.
 */
public record RoomNumber(String value) {

    public static final int MAX_LENGTH = 16;

    public RoomNumber {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("RoomNumber must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "RoomNumber must be at most " + MAX_LENGTH + " characters, was " + trimmed.length());
        }
        value = trimmed;
    }
}

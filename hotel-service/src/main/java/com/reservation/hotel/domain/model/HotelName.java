package com.reservation.hotel.domain.model;

import java.util.Objects;

/**
 * Hotel 이름 VO. 공백만으로 이루어지거나 DB 컬럼 길이를 초과하는 값은 허용하지 않는다.
 *
 * <p>DB 상한(VARCHAR(200)) 을 VO 에서 재확인해 infrastructure 경계에서 실패가 터지기 전에
 * 도메인 불변식으로 거부한다.
 */
public record HotelName(String value) {

    public static final int MAX_LENGTH = 200;

    public HotelName {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("HotelName must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "HotelName must be at most " + MAX_LENGTH + " characters, was " + trimmed.length());
        }
        value = trimmed;
    }
}

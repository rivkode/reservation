package com.reservation.hotel.domain.model;

/**
 * 호텔 등급 VO. 정수 1~5 만 허용하며 소수 등급은 본 프로젝트 범위 외.
 */
public record StarRating(int value) {

    public static final int MIN = 1;
    public static final int MAX = 5;

    public StarRating {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(
                "StarRating must be in [" + MIN + ", " + MAX + "], was " + value);
        }
    }
}

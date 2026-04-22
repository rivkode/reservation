package com.reservation.hotel.domain.model;

/**
 * 객실 층수 VO. 지하층(음수) 를 허용한다. 상한은 현실적 호텔 최대치 고려 200 층.
 */
public record Floor(int value) {

    public static final int MIN = -10;
    public static final int MAX = 200;

    public Floor {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(
                "Floor must be in [" + MIN + ", " + MAX + "], was " + value);
        }
    }
}

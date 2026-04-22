package com.reservation.hotel.domain.model;

/**
 * RoomType 최대 수용 인원 VO. 1 이상 10 이하로 제한한다. 스위트룸 이상의 특수 케이스는
 * 본 프로젝트 범위 외 — 향후 필요 시 상한을 완화한다.
 */
public record MaxOccupancy(int value) {

    public static final int MIN = 1;
    public static final int MAX = 10;

    public MaxOccupancy {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(
                "MaxOccupancy must be in [" + MIN + ", " + MAX + "], was " + value);
        }
    }
}

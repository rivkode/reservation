package com.reservation.reservation.domain.model;

/**
 * 예약 인원 수 VO.
 *
 * <p>최소 1 인 이상이라는 도메인 불변식을 VO 에서 직접 방어한다. 객실 타입별 수용 인원
 * 상한 검증은 hotel-service 의 RoomType 책임이며 본 서비스는 외부 SoT 위반 가능성만
 * Application 계층에서 추가 방어한다 (PR-3.x 또는 운영상 제약).
 */
public record NumberOfGuests(int value) {

    public NumberOfGuests {
        if (value < 1) {
            throw new IllegalArgumentException("numberOfGuests must be >= 1, was " + value);
        }
    }

    public static NumberOfGuests of(int value) {
        return new NumberOfGuests(value);
    }
}

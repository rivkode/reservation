package com.reservation.hotel.presentation.exception;

import com.reservation.common.exception.CommonErrorCode;

/**
 * hotel-service 전용 도메인 에러 코드. 상위 분류는 {@link CommonErrorCode} 에 매핑되며
 * advice 가 응답에는 본 enum 의 {@code name()} 을 실어 클라이언트가 구체 원인을
 * 구분하게 한다.
 */
public enum HotelErrorCode {

    HOTEL_NOT_FOUND(CommonErrorCode.RESOURCE_NOT_FOUND),
    ROOM_TYPE_NOT_FOUND(CommonErrorCode.RESOURCE_NOT_FOUND),
    ROOM_NOT_FOUND(CommonErrorCode.RESOURCE_NOT_FOUND),
    DUPLICATE_ROOM_TYPE_NAME(CommonErrorCode.CONFLICT),
    DUPLICATE_ROOM_NUMBER(CommonErrorCode.CONFLICT),
    ROOM_TYPE_HOTEL_MISMATCH(CommonErrorCode.CONFLICT),
    INVALID_ROOM_STATE_TRANSITION(CommonErrorCode.CONFLICT);

    private final CommonErrorCode category;

    HotelErrorCode(CommonErrorCode category) {
        this.category = category;
    }

    public CommonErrorCode category() {
        return category;
    }

    public int defaultStatus() {
        return category.defaultStatus();
    }
}

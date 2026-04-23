package com.reservation.rate.presentation.exception;

import com.reservation.common.exception.CommonErrorCode;

/**
 * rate-service 전용 도메인 에러 코드. 상위 분류는 {@link CommonErrorCode} 에 매핑되며
 * advice 가 응답에는 본 enum 의 {@code name()} 을 실어 클라이언트가 구체 원인을
 * 구분하게 한다.
 */
public enum RateErrorCode {

    RATE_NOT_FOUND(CommonErrorCode.RESOURCE_NOT_FOUND),
    DUPLICATE_RATE(CommonErrorCode.CONFLICT);

    private final CommonErrorCode category;

    RateErrorCode(CommonErrorCode category) {
        this.category = category;
    }

    public CommonErrorCode category() {
        return category;
    }

    public int defaultStatus() {
        return category.defaultStatus();
    }
}

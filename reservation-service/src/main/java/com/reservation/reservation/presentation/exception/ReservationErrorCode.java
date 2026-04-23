package com.reservation.reservation.presentation.exception;

import com.reservation.common.exception.CommonErrorCode;

/**
 * reservation-service 전용 도메인 에러 코드. 상위 분류는 {@link CommonErrorCode} 에
 * 매핑되며 advice 가 응답에는 본 enum 의 {@code name()} 을 실어 클라이언트가 구체 원인을
 * 구분하게 한다.
 *
 * <p>매핑 정책:
 * <ul>
 *   <li>{@code GUEST_NOT_FOUND} · {@code INVENTORY_NOT_INITIALIZED} → 404</li>
 *   <li>{@code INSUFFICIENT_INVENTORY} → 409 (재고 부족 — 같은 요청 즉시 재시도 무의미)</li>
 *   <li>{@code GUEST_SERVICE_UNAVAILABLE} · {@code RATE_SERVICE_UNAVAILABLE} → 503
 *       (외부 서비스 일시 장애 — 클라이언트 재시도 권장)</li>
 *   <li>{@code RATE_NOT_FOUND} → 404 (요청한 호텔·객실타입·날짜의 요금 미등록)</li>
 * </ul>
 */
public enum ReservationErrorCode {

    GUEST_NOT_FOUND(CommonErrorCode.RESOURCE_NOT_FOUND),
    INVENTORY_NOT_INITIALIZED(CommonErrorCode.RESOURCE_NOT_FOUND),
    RATE_NOT_FOUND(CommonErrorCode.RESOURCE_NOT_FOUND),
    INSUFFICIENT_INVENTORY(CommonErrorCode.CONFLICT),
    GUEST_SERVICE_UNAVAILABLE(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE),
    RATE_SERVICE_UNAVAILABLE(CommonErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);

    private final CommonErrorCode category;

    ReservationErrorCode(CommonErrorCode category) {
        this.category = category;
    }

    public CommonErrorCode category() {
        return category;
    }

    public int defaultStatus() {
        return category.defaultStatus();
    }
}

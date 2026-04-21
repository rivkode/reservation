package com.reservation.common.exception;

/**
 * 모든 서비스가 공통으로 사용하는 에러 코드 카테고리.
 *
 * <p>{@link #defaultStatus()} 는 Presentation 계층의 {@code @RestControllerAdvice}
 * 가 참고할 기본 HTTP status 값이다. 서비스별로 다른 status 가 필요하면 advice 에서
 * override 할 수 있다. 본 enum 은 {@code spring-web} 의 {@code HttpStatus} 에
 * 의존하지 않도록 순수 int 로 표현해 common-infrastructure 가 web 스택까지 끌어오지
 * 않게 한다.
 *
 * <p>서비스별 도메인 에러 코드는 각자 별도 enum 으로 정의하며 본 enum 을
 * 확장하거나 상속하지 않는다 (계층 경계 보존).
 */
public enum CommonErrorCode {

    /** 요청 값이 유효하지 않음. */
    VALIDATION_FAILED(400),

    /** 대상 리소스가 존재하지 않음. */
    RESOURCE_NOT_FOUND(404),

    /** 동시 변경 · 중복 등 상태 충돌. */
    CONFLICT(409),

    /** 인증되지 않았거나 토큰이 유효하지 않음. */
    UNAUTHENTICATED(401),

    /** 인증은 되었으나 권한 없음. */
    FORBIDDEN(403),

    /** 외부 의존 서비스(gRPC 등) 가 일시적으로 사용 불가. */
    EXTERNAL_SERVICE_UNAVAILABLE(503),

    /** 분류되지 않은 서버 측 실패. */
    INTERNAL_ERROR(500);

    private final int defaultStatus;

    CommonErrorCode(int defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    /** 이 코드가 제시하는 기본 HTTP status. 서비스별 advice 는 override 가능. */
    public int defaultStatus() {
        return defaultStatus;
    }
}

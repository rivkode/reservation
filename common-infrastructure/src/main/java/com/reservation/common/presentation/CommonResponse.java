package com.reservation.common.presentation;

/**
 * 모든 REST 성공 응답을 감싸는 공용 래퍼. 응답 본문을 `data` 한 필드로 통일해
 * 클라이언트 파싱 코드 · 로깅 필터 · 스키마 계약을 서비스간 일관되게 유지한다.
 *
 * <p>HTTP status 는 {@link org.springframework.http.ResponseEntity} 에서 지정하고,
 * 본 래퍼는 body 만 표준화한다. Location 헤더는 사용하지 않으며 생성 결과도 동일
 * 래퍼에 `data` 로 실어 반환한다. 에러 응답은 {@link com.reservation.common.exception.ErrorResponse}
 * 가 별도 포맷으로 다룬다 (RFC 7807 스타일).
 *
 * @param <T> 실제 응답 payload 타입
 */
public record CommonResponse<T>(T data) {

    public static <T> CommonResponse<T> of(T data) {
        return new CommonResponse<>(data);
    }
}

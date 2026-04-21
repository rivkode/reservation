package com.reservation.common.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * REST API 공통 에러 응답 포맷. 각 서비스 Presentation 계층의
 * {@code @RestControllerAdvice} 에서 예외를 본 record 로 변환해 반환한다.
 *
 * <p>필드는 RFC 7807 Problem Details 스타일의 간소판을 따른다. 필요한 서비스는
 * 본 record 를 wrapping 하는 자체 응답 타입을 만들 수 있다.
 *
 * @param timestamp        응답 생성 시각 (UTC)
 * @param status           HTTP status code
 * @param code             {@link CommonErrorCode} 또는 서비스별 코드 문자열
 * @param message          사람이 읽을 수 있는 메시지 (로깅 가능 정보만, PII 금지)
 * @param path             요청 경로 (예: {@code /api/v1/reservations})
 * @param fieldViolations  입력 필드별 위반 목록 (없으면 empty)
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    List<FieldViolation> fieldViolations
) {

    public ErrorResponse {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        fieldViolations = fieldViolations == null ? List.of() : List.copyOf(fieldViolations);
    }

    /**
     * 지정된 Clock 으로 timestamp 를 만들어 ErrorResponse 를 생성한다.
     * 테스트에서 {@link Clock#fixed} 주입이 가능하도록 Clock 을 파라미터로 받는다.
     */
    public static ErrorResponse of(
        int status,
        CommonErrorCode code,
        String message,
        String path,
        List<FieldViolation> fieldViolations,
        Clock clock
    ) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(clock, "clock");
        return new ErrorResponse(
            Instant.now(clock),
            status,
            code.name(),
            message,
            path,
            fieldViolations
        );
    }

    public record FieldViolation(String field, String reason) {

        public FieldViolation {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(reason, "reason");
        }
    }
}

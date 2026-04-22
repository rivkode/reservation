package com.reservation.rate.presentation.exception;

import com.reservation.common.exception.CommonErrorCode;
import com.reservation.common.exception.ErrorResponse;
import com.reservation.rate.domain.exception.DuplicateRateException;
import com.reservation.rate.domain.exception.RateNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * rate-service 전역 예외 → {@link ErrorResponse} 변환. 도메인 예외만 분기하고
 * 그 외는 최상위 {@link RuntimeException} 을 {@code 500 INTERNAL_ERROR} 로 묶어 로그로
 * 원인을 남긴다. hotel-service HotelExceptionHandler 와 동일한 패턴.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class RateExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(RateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRateNotFound(RateNotFoundException e, HttpServletRequest req) {
        return build(RateErrorCode.RATE_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(DuplicateRateException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRate(DuplicateRateException e, HttpServletRequest req) {
        return build(RateErrorCode.DUPLICATE_RATE, e.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest req) {
        int status = CommonErrorCode.VALIDATION_FAILED.defaultStatus();
        ErrorResponse response = ErrorResponse.of(status, CommonErrorCode.VALIDATION_FAILED,
            e.getMessage(), req.getRequestURI(), List.of(), clock);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(RuntimeException e, HttpServletRequest req) {
        log.error("Unhandled error at {}", req.getRequestURI(), e);
        int status = CommonErrorCode.INTERNAL_ERROR.defaultStatus();
        ErrorResponse response = ErrorResponse.of(status, CommonErrorCode.INTERNAL_ERROR,
            "Internal server error", req.getRequestURI(), List.of(), clock);
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<ErrorResponse> build(RateErrorCode code, String message, HttpServletRequest req) {
        int status = code.defaultStatus();
        ErrorResponse response = new ErrorResponse(
            Instant.now(clock),
            status,
            code.name(),
            message,
            req.getRequestURI(),
            List.of()
        );
        return ResponseEntity.status(status).body(response);
    }
}

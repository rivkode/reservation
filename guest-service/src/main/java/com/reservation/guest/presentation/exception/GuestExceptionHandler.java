package com.reservation.guest.presentation.exception;

import com.reservation.common.exception.CommonErrorCode;
import com.reservation.common.exception.ErrorResponse;
import com.reservation.guest.domain.exception.DuplicateEmailException;
import com.reservation.guest.domain.exception.GuestNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * guest-service 전역 예외 → {@link ErrorResponse} 변환. rate-service 와 동일 패턴.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GuestExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(GuestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGuestNotFound(GuestNotFoundException e, HttpServletRequest req) {
        return build(GuestErrorCode.GUEST_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException e, HttpServletRequest req) {
        return build(GuestErrorCode.DUPLICATE_EMAIL, e.getMessage(), req);
    }

    /**
     * 트랜잭션 flush/commit 시점에 올라오는 DB 제약 위반. guest 스키마의 유일한
     * UNIQUE 제약은 {@code uk_guest_email} 이므로 모두 DUPLICATE_EMAIL 로 매핑.
     * Application Service 의 선제 {@code existsByEmail} 검증과 이 매핑이 함께
     * race condition 을 409 로 통일한다 (code-reviewer H1 반영).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e,
                                                                      HttpServletRequest req) {
        log.warn("DB integrity violation at {} — mapping to DUPLICATE_EMAIL: {}",
            req.getRequestURI(), e.getMostSpecificCause().getMessage());
        return build(GuestErrorCode.DUPLICATE_EMAIL,
            "Guest email already exists (race condition)", req);
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

    private ResponseEntity<ErrorResponse> build(GuestErrorCode code, String message, HttpServletRequest req) {
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

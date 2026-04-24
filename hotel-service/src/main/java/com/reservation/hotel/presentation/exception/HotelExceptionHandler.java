package com.reservation.hotel.presentation.exception;

import com.reservation.common.exception.CommonErrorCode;
import com.reservation.common.exception.ErrorResponse;
import com.reservation.hotel.domain.exception.DuplicateRoomNumberException;
import com.reservation.hotel.domain.exception.DuplicateRoomTypeNameException;
import com.reservation.hotel.domain.exception.HotelNotFoundException;
import com.reservation.hotel.domain.exception.InvalidRoomStateTransitionException;
import com.reservation.hotel.domain.exception.RoomNotFoundException;
import com.reservation.hotel.domain.exception.RoomTypeHotelMismatchException;
import com.reservation.hotel.domain.exception.RoomTypeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.util.List;

/**
 * hotel-service 전역 예외 → {@link ErrorResponse} 변환. 도메인 예외만 분기하고
 * 그 외는 최상위 {@link RuntimeException} 을 {@code 500 INTERNAL_ERROR} 로 묶어 로그로
 * 원인을 남긴다. Dev/Ops 가 원인 추적 시 로그의 {@code code} 를 사용한다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class HotelExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(HotelNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHotelNotFound(HotelNotFoundException e, HttpServletRequest req) {
        return build(HotelErrorCode.HOTEL_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(RoomTypeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomTypeNotFound(RoomTypeNotFoundException e, HttpServletRequest req) {
        return build(HotelErrorCode.ROOM_TYPE_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(RoomNotFoundException e, HttpServletRequest req) {
        return build(HotelErrorCode.ROOM_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(DuplicateRoomTypeNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRoomTypeName(DuplicateRoomTypeNameException e, HttpServletRequest req) {
        return build(HotelErrorCode.DUPLICATE_ROOM_TYPE_NAME, e.getMessage(), req);
    }

    @ExceptionHandler(DuplicateRoomNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRoomNumber(DuplicateRoomNumberException e, HttpServletRequest req) {
        return build(HotelErrorCode.DUPLICATE_ROOM_NUMBER, e.getMessage(), req);
    }

    @ExceptionHandler(RoomTypeHotelMismatchException.class)
    public ResponseEntity<ErrorResponse> handleRoomTypeHotelMismatch(RoomTypeHotelMismatchException e, HttpServletRequest req) {
        return build(HotelErrorCode.ROOM_TYPE_HOTEL_MISMATCH, e.getMessage(), req);
    }

    @ExceptionHandler(InvalidRoomStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRoomStateTransition(InvalidRoomStateTransitionException e, HttpServletRequest req) {
        return build(HotelErrorCode.INVALID_ROOM_STATE_TRANSITION, e.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest req) {
        int status = CommonErrorCode.VALIDATION_FAILED.defaultStatus();
        ErrorResponse response = ErrorResponse.of(status, CommonErrorCode.VALIDATION_FAILED,
            e.getMessage(), req.getRequestURI(), List.of(), clock);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Redis 장애 → 503. ADR 0004 "가용성 조회는 Redis 만" 정책에 따라 fallback 없이 즉시
     * 503 을 반환한다. {@link RedisConnectionFailureException} 는 {@link RedisSystemException}
     * 의 하위지만 의도를 명확히 하기 위해 둘 다 명시. Spring Data JPA 의
     * {@code DataAccessException} (예: {@code DataIntegrityViolationException}) 까지 포괄적으로
     * 잡지 않도록 Redis 계열로 좁힌다 — 호텔 등록/룸 생성 등 MySQL 경로의 예외는 각자 도메인
     * 핸들러가 409/404 로 매핑.
     */
    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<ErrorResponse> handleRedisUnavailable(RuntimeException e, HttpServletRequest req) {
        log.warn("Redis unavailable at {}: {}", req.getRequestURI(), e.getMessage());
        return build(HotelErrorCode.AVAILABILITY_CACHE_UNAVAILABLE, "Availability cache unavailable", req);
    }

    /**
     * 쿼리 파라미터 누락 / 타입 불일치 → 400. Spring 기본 {@code RuntimeException} 핸들러로
     * 떨어지면 500 이 되어 버리므로 명시적으로 400 으로 내려보낸다 (AvailabilityController
     * 의 @RequestParam {@code LocalDate} 파싱 실패 포함).
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest req) {
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

    private ResponseEntity<ErrorResponse> build(HotelErrorCode code, String message, HttpServletRequest req) {
        int status = code.defaultStatus();
        ErrorResponse response = new ErrorResponse(
            java.time.Instant.now(clock),
            status,
            code.name(),
            message,
            req.getRequestURI(),
            List.of()
        );
        return ResponseEntity.status(status).body(response);
    }
}

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

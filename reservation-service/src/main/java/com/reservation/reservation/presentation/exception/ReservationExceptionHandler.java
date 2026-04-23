package com.reservation.reservation.presentation.exception;

import com.reservation.common.exception.CommonErrorCode;
import com.reservation.common.exception.ErrorResponse;
import com.reservation.reservation.domain.exception.CurrencyMismatchException;
import com.reservation.reservation.domain.exception.InsufficientInventoryException;
import com.reservation.reservation.domain.exception.InventoryNotInitializedException;
import com.reservation.reservation.domain.exception.ReservationAlreadyCancelledException;
import com.reservation.reservation.domain.exception.ReservationNotFoundException;
import com.reservation.reservation.domain.service.GuestVerificationPort;
import com.reservation.reservation.domain.service.RoomTypeRateQuotePort;
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
 * reservation-service 전역 예외 → {@link ErrorResponse} 변환.
 *
 * <p>외부 서비스 통신 실패 ({@link GuestVerificationPort.GuestVerificationUnavailableException}
 * · {@link RoomTypeRateQuotePort.RateServiceUnavailableException}) 는 503 으로 매핑해
 * 클라이언트가 재시도할 수 있게 한다. {@link CurrencyMismatchException} 은 외부
 * SoT(rate-service) 의 응답 정합성 오류이므로 500 (INTERNAL_ERROR).
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class ReservationExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(GuestVerificationPort.GuestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGuestNotFound(
        GuestVerificationPort.GuestNotFoundException e, HttpServletRequest req) {
        return build(ReservationErrorCode.GUEST_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(InventoryNotInitializedException.class)
    public ResponseEntity<ErrorResponse> handleInventoryNotInitialized(
        InventoryNotInitializedException e, HttpServletRequest req) {
        return build(ReservationErrorCode.INVENTORY_NOT_INITIALIZED, e.getMessage(), req);
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientInventory(
        InsufficientInventoryException e, HttpServletRequest req) {
        return build(ReservationErrorCode.INSUFFICIENT_INVENTORY, e.getMessage(), req);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReservationNotFound(
        ReservationNotFoundException e, HttpServletRequest req) {
        return build(ReservationErrorCode.RESERVATION_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(ReservationAlreadyCancelledException.class)
    public ResponseEntity<ErrorResponse> handleReservationAlreadyCancelled(
        ReservationAlreadyCancelledException e, HttpServletRequest req) {
        return build(ReservationErrorCode.RESERVATION_ALREADY_CANCELLED, e.getMessage(), req);
    }

    @ExceptionHandler(RoomTypeRateQuotePort.RoomTypeRateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRateNotFound(
        RoomTypeRateQuotePort.RoomTypeRateNotFoundException e, HttpServletRequest req) {
        return build(ReservationErrorCode.RATE_NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(GuestVerificationPort.GuestVerificationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleGuestUnavailable(
        GuestVerificationPort.GuestVerificationUnavailableException e, HttpServletRequest req) {
        log.warn("guest-service unavailable while creating reservation: {}", e.getMessage());
        return build(ReservationErrorCode.GUEST_SERVICE_UNAVAILABLE, e.getMessage(), req);
    }

    @ExceptionHandler(RoomTypeRateQuotePort.RateServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRateUnavailable(
        RoomTypeRateQuotePort.RateServiceUnavailableException e, HttpServletRequest req) {
        log.warn("rate-service unavailable while creating reservation: {}", e.getMessage());
        return build(ReservationErrorCode.RATE_SERVICE_UNAVAILABLE, e.getMessage(), req);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(
        CurrencyMismatchException e, HttpServletRequest req) {
        log.error("Currency mismatch in rate quote — upstream rate-service inconsistency: {}",
            e.getMessage());
        int status = CommonErrorCode.INTERNAL_ERROR.defaultStatus();
        ErrorResponse response = ErrorResponse.of(status, CommonErrorCode.INTERNAL_ERROR,
            e.getMessage(), req.getRequestURI(), List.of(), clock);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
        IllegalArgumentException e, HttpServletRequest req) {
        int status = CommonErrorCode.VALIDATION_FAILED.defaultStatus();
        ErrorResponse response = ErrorResponse.of(status, CommonErrorCode.VALIDATION_FAILED,
            e.getMessage(), req.getRequestURI(), List.of(), clock);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
        RuntimeException e, HttpServletRequest req) {
        log.error("Unhandled error at {}", req.getRequestURI(), e);
        int status = CommonErrorCode.INTERNAL_ERROR.defaultStatus();
        ErrorResponse response = ErrorResponse.of(status, CommonErrorCode.INTERNAL_ERROR,
            "Internal server error", req.getRequestURI(), List.of(), clock);
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<ErrorResponse> build(ReservationErrorCode code, String message,
                                                 HttpServletRequest req) {
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

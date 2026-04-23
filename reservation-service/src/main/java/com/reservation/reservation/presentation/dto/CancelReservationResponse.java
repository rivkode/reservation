package com.reservation.reservation.presentation.dto;

import com.reservation.reservation.application.dto.CancelReservationResult;
import com.reservation.reservation.domain.model.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 예약 취소 REST 응답 DTO. PRD §13 Q3 의 "상태 기록만" 정책에 따라 정책명/환불률만
 * 노출하며 실제 환불 처리 (결제 시스템 연동) 는 본 응답 범위 외 — 결제 PRD 도입 시
 * 별도 필드로 확장.
 */
public record CancelReservationResponse(
    String reservationId,
    ReservationStatus status,
    Instant cancelledAt,
    BigDecimal refundRate,
    String cancellationPolicyName
) {

    public static CancelReservationResponse of(CancelReservationResult result) {
        return new CancelReservationResponse(
            result.reservationId(),
            result.status(),
            result.cancelledAt(),
            result.refundRate(),
            result.cancellationPolicyName()
        );
    }
}

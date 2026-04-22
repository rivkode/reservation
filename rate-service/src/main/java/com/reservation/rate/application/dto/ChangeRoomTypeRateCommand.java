package com.reservation.rate.application.dto;

/**
 * 기존 요금의 금액 · 통화 변경 Command. {@code rateId} 는 등록 응답에서 받은 surrogate
 * 식별자 (관리자 화면 내부에서만 보유).
 */
public record ChangeRoomTypeRateCommand(
    String rateId,
    long amount,
    String currency
) {
}

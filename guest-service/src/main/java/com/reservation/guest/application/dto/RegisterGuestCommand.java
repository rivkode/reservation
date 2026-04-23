package com.reservation.guest.application.dto;

/**
 * 투숙객 등록 Command. 원시 타입으로 받아 Application Service 가 VO 로 변환한다.
 */
public record RegisterGuestCommand(
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {
}

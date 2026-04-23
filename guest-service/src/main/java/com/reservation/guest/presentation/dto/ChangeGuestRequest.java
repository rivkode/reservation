package com.reservation.guest.presentation.dto;

import com.reservation.guest.application.dto.ChangeGuestCommand;

/**
 * PATCH 요청 Body. 각 필드는 nullable — {@code null} 은 "변경 없음" 을 의미한다.
 * firstName · lastName 은 GuestName VO 가 묶음이므로 함께 제공되어야 한다
 * (ChangeGuestCommand 주석 · Application Service 검증 참조).
 */
public record ChangeGuestRequest(
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {

    public ChangeGuestCommand toCommand(String guestId) {
        return new ChangeGuestCommand(guestId, firstName, lastName, email, phoneNumber);
    }
}

package com.reservation.guest.presentation.dto;

import com.reservation.guest.application.dto.RegisterGuestCommand;

public record RegisterGuestRequest(
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {

    public RegisterGuestCommand toCommand() {
        return new RegisterGuestCommand(firstName, lastName, email, phoneNumber);
    }
}

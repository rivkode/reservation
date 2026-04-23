package com.reservation.guest.presentation.dto;

import com.reservation.guest.application.dto.GuestResult;

public record GuestResponse(
    String id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {

    public static GuestResponse of(GuestResult result) {
        return new GuestResponse(
            result.id(), result.firstName(), result.lastName(),
            result.email(), result.phoneNumber());
    }
}

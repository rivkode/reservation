package com.reservation.guest.application.dto;

import com.reservation.guest.domain.model.Guest;

/**
 * Application 계층의 Guest 표현. Presentation / gRPC adapter 가 공용으로 사용한다.
 * 도메인 객체를 바깥으로 노출하지 않기 위한 경계.
 */
public record GuestResult(
    String id,
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {

    public static GuestResult of(Guest guest) {
        return new GuestResult(
            guest.id().asString(),
            guest.name().firstName(),
            guest.name().lastName(),
            guest.email().value(),
            guest.phoneNumber().value()
        );
    }
}

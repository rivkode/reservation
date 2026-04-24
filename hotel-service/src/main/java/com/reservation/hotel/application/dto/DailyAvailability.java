package com.reservation.hotel.application.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 하루치 가용성 결과. {@link RoomAvailabilitySnapshot} 에서 내부용 {@code updatedAt} 을
 * 제외하고 외부 응답 에 필요한 필드만 남긴다.
 */
public record DailyAvailability(LocalDate date, int available, int total) {

    public DailyAvailability {
        Objects.requireNonNull(date, "date");
    }
}

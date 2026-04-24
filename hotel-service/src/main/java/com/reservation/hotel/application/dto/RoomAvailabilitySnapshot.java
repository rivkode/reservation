package com.reservation.hotel.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Redis {@code RoomAvailabilityView} 한 날짜의 스냅샷. Application ↔ Infrastructure
 * (Redis) 경계에서 주고받는 표준 표현이다. Presentation 응답의 {@code DailyAvailability}
 * 와는 별도 — 본 record 는 {@code updatedAt} 을 포함해 Application 이 {@code staleUntil}
 * 을 계산한다.
 */
public record RoomAvailabilitySnapshot(LocalDate date, int available, int total, Instant updatedAt) {

    public RoomAvailabilitySnapshot {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (available < 0) {
            throw new IllegalArgumentException("available must be non-negative, was " + available);
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative, was " + total);
        }
    }
}

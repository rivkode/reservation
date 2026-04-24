package com.reservation.hotel.presentation.dto;

import java.time.LocalDate;

public record DailyAvailabilityResponse(LocalDate date, int available, int total) {
}

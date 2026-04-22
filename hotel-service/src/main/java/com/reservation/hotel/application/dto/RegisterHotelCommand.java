package com.reservation.hotel.application.dto;

import java.util.Set;

public record RegisterHotelCommand(
    String name,
    String addressStreet,
    String addressCity,
    String addressCountry,
    int starRating,
    Set<String> amenities
) {
}

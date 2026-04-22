package com.reservation.hotel.application.dto;

import com.reservation.hotel.domain.model.Amenity;
import com.reservation.hotel.domain.model.Hotel;

import java.util.Set;
import java.util.stream.Collectors;

public record HotelResult(
    String id,
    String name,
    String addressStreet,
    String addressCity,
    String addressCountry,
    int starRating,
    Set<String> amenities
) {

    public static HotelResult of(Hotel hotel) {
        return new HotelResult(
            hotel.id().asString(),
            hotel.name().value(),
            hotel.address().street(),
            hotel.address().city(),
            hotel.address().country(),
            hotel.starRating().value(),
            hotel.amenities().stream().map(Amenity::name).collect(Collectors.toUnmodifiableSet())
        );
    }
}

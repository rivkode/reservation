package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.HotelResult;

import java.util.Set;

public record HotelResponse(
    String id,
    String name,
    AddressResponse address,
    int starRating,
    Set<String> amenities
) {

    public static HotelResponse of(HotelResult result) {
        return new HotelResponse(
            result.id(),
            result.name(),
            new AddressResponse(result.addressStreet(), result.addressCity(), result.addressCountry()),
            result.starRating(),
            result.amenities()
        );
    }

    public record AddressResponse(String street, String city, String country) {
    }
}

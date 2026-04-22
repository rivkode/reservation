package com.reservation.hotel.domain.exception;

import com.reservation.hotel.domain.model.HotelId;

public class HotelNotFoundException extends RuntimeException {

    private final HotelId hotelId;

    public HotelNotFoundException(HotelId hotelId) {
        super("Hotel not found: " + hotelId.asString());
        this.hotelId = hotelId;
    }

    public HotelId hotelId() {
        return hotelId;
    }
}

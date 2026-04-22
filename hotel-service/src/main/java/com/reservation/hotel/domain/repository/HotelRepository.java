package com.reservation.hotel.domain.repository;

import com.reservation.hotel.domain.model.Hotel;
import com.reservation.hotel.domain.model.HotelId;

import java.util.Optional;

public interface HotelRepository {

    Hotel save(Hotel hotel);

    Optional<Hotel> findById(HotelId id);

    boolean existsById(HotelId id);
}

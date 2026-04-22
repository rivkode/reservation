package com.reservation.hotel.domain.repository;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomType;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.domain.model.RoomTypeName;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository {

    RoomType save(RoomType roomType);

    Optional<RoomType> findById(RoomTypeId id);

    List<RoomType> findByHotelId(HotelId hotelId);

    boolean existsByHotelIdAndName(HotelId hotelId, RoomTypeName name);
}

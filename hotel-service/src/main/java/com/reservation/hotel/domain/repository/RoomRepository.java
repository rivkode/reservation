package com.reservation.hotel.domain.repository;

import com.reservation.hotel.domain.model.Floor;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.Room;
import com.reservation.hotel.domain.model.RoomId;
import com.reservation.hotel.domain.model.RoomNumber;

import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    Room save(Room room);

    Optional<Room> findById(RoomId id);

    List<Room> findByHotelId(HotelId hotelId);

    boolean existsByHotelIdAndFloorAndNumber(HotelId hotelId, Floor floor, RoomNumber number);
}

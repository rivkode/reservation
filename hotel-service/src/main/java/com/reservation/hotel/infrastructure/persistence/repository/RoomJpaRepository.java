package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.infrastructure.persistence.entity.RoomJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomJpaRepository extends JpaRepository<RoomJpaEntity, UUID> {

    List<RoomJpaEntity> findByHotelId(UUID hotelId);

    boolean existsByHotelIdAndFloorAndNumber(UUID hotelId, int floor, String number);
}

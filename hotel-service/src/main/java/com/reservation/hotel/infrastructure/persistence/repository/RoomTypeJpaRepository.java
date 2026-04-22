package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.infrastructure.persistence.entity.RoomTypeJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomTypeJpaRepository extends JpaRepository<RoomTypeJpaEntity, UUID> {

    List<RoomTypeJpaEntity> findByHotelId(UUID hotelId);

    boolean existsByHotelIdAndName(UUID hotelId, String name);
}

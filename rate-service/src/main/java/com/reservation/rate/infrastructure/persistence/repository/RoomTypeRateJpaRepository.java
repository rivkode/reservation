package com.reservation.rate.infrastructure.persistence.repository;

import com.reservation.rate.infrastructure.persistence.entity.RoomTypeRateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RoomTypeRateJpaRepository extends JpaRepository<RoomTypeRateJpaEntity, UUID> {

    boolean existsByHotelIdAndRoomTypeIdAndRateDate(UUID hotelId, UUID roomTypeId, LocalDate rateDate);

    List<RoomTypeRateJpaEntity> findByHotelIdAndRoomTypeIdAndRateDateBetweenOrderByRateDateAsc(
        UUID hotelId, UUID roomTypeId, LocalDate from, LocalDate to);
}

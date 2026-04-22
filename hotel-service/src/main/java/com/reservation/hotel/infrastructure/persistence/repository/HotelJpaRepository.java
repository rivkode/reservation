package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.infrastructure.persistence.entity.HotelJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HotelJpaRepository extends JpaRepository<HotelJpaEntity, UUID> {
}

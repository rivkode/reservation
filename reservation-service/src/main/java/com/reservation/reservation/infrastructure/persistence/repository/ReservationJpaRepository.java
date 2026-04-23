package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.reservation.infrastructure.persistence.entity.ReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, UUID> {
}

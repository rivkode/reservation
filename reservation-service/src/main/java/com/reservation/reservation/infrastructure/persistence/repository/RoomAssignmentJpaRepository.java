package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.reservation.infrastructure.persistence.entity.RoomAssignmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomAssignmentJpaRepository extends JpaRepository<RoomAssignmentJpaEntity, UUID> {
}

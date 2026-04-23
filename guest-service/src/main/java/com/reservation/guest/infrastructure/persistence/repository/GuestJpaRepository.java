package com.reservation.guest.infrastructure.persistence.repository;

import com.reservation.guest.infrastructure.persistence.entity.GuestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuestJpaRepository extends JpaRepository<GuestJpaEntity, UUID> {

    boolean existsByEmail(String email);

    List<GuestJpaEntity> findAllByIdIn(Collection<UUID> ids);
}

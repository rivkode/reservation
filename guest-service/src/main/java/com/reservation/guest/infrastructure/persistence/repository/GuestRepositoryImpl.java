package com.reservation.guest.infrastructure.persistence.repository;

import com.reservation.guest.domain.model.Email;
import com.reservation.guest.domain.model.Guest;
import com.reservation.guest.domain.model.GuestId;
import com.reservation.guest.domain.repository.GuestRepository;
import com.reservation.guest.infrastructure.persistence.entity.GuestJpaEntity;
import com.reservation.guest.infrastructure.persistence.mapper.GuestJpaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GuestRepositoryImpl implements GuestRepository {

    private final GuestJpaRepository jpaRepository;

    @Override
    public Guest save(Guest guest) {
        GuestJpaEntity saved = jpaRepository.save(GuestJpaMapper.toEntity(guest));
        return GuestJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<Guest> findById(GuestId id) {
        return jpaRepository.findById(id.value()).map(GuestJpaMapper::toDomain);
    }

    @Override
    public List<Guest> findAllByIds(Collection<GuestId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<UUID> uuids = ids.stream().map(GuestId::value).toList();
        return jpaRepository.findAllByIdIn(uuids).stream()
            .map(GuestJpaMapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }
}

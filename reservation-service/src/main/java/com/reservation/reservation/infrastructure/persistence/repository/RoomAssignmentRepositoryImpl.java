package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.reservation.domain.model.RoomAssignment;
import com.reservation.reservation.domain.model.RoomId;
import com.reservation.reservation.domain.repository.RoomAssignmentRepository;
import com.reservation.reservation.infrastructure.persistence.mapper.RoomAssignmentJpaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomAssignmentRepositoryImpl implements RoomAssignmentRepository {

    private final RoomAssignmentJpaRepository jpaRepository;

    @Override
    public Optional<RoomAssignment> findByRoomId(RoomId roomId) {
        return jpaRepository.findById(roomId.value()).map(RoomAssignmentJpaMapper::toDomain);
    }

    @Override
    public void save(RoomAssignment assignment) {
        jpaRepository.save(RoomAssignmentJpaMapper.toEntity(assignment));
    }

    @Override
    public void deleteByRoomId(RoomId roomId) {
        jpaRepository.deleteById(roomId.value());
    }
}

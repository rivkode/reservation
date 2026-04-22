package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.domain.model.Floor;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.Room;
import com.reservation.hotel.domain.model.RoomId;
import com.reservation.hotel.domain.model.RoomNumber;
import com.reservation.hotel.domain.repository.RoomRepository;
import com.reservation.hotel.infrastructure.persistence.entity.RoomJpaEntity;
import com.reservation.hotel.infrastructure.persistence.mapper.RoomJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RoomRepositoryImpl implements RoomRepository {

    private final RoomJpaRepository jpaRepository;

    public RoomRepositoryImpl(RoomJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Room save(Room room) {
        RoomJpaEntity entity = RoomJpaMapper.toEntity(room);
        RoomJpaEntity saved = jpaRepository.save(entity);
        return RoomJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<Room> findById(RoomId id) {
        return jpaRepository.findById(id.value()).map(RoomJpaMapper::toDomain);
    }

    @Override
    public List<Room> findByHotelId(HotelId hotelId) {
        return jpaRepository.findByHotelId(hotelId.value()).stream()
            .map(RoomJpaMapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByHotelIdAndFloorAndNumber(HotelId hotelId, Floor floor, RoomNumber number) {
        return jpaRepository.existsByHotelIdAndFloorAndNumber(hotelId.value(), floor.value(), number.value());
    }
}

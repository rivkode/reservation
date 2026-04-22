package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomType;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.domain.model.RoomTypeName;
import com.reservation.hotel.domain.repository.RoomTypeRepository;
import com.reservation.hotel.infrastructure.persistence.entity.RoomTypeJpaEntity;
import com.reservation.hotel.infrastructure.persistence.mapper.RoomTypeJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RoomTypeRepositoryImpl implements RoomTypeRepository {

    private final RoomTypeJpaRepository jpaRepository;

    public RoomTypeRepositoryImpl(RoomTypeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RoomType save(RoomType roomType) {
        RoomTypeJpaEntity entity = RoomTypeJpaMapper.toEntity(roomType);
        RoomTypeJpaEntity saved = jpaRepository.save(entity);
        return RoomTypeJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<RoomType> findById(RoomTypeId id) {
        return jpaRepository.findById(id.value()).map(RoomTypeJpaMapper::toDomain);
    }

    @Override
    public List<RoomType> findByHotelId(HotelId hotelId) {
        return jpaRepository.findByHotelId(hotelId.value()).stream()
            .map(RoomTypeJpaMapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByHotelIdAndName(HotelId hotelId, RoomTypeName name) {
        return jpaRepository.existsByHotelIdAndName(hotelId.value(), name.value());
    }
}

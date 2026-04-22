package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.domain.model.Hotel;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.repository.HotelRepository;
import com.reservation.hotel.infrastructure.persistence.entity.HotelJpaEntity;
import com.reservation.hotel.infrastructure.persistence.mapper.HotelJpaMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class HotelRepositoryImpl implements HotelRepository {

    private final HotelJpaRepository jpaRepository;

    public HotelRepositoryImpl(HotelJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Hotel save(Hotel hotel) {
        HotelJpaEntity entity = HotelJpaMapper.toEntity(hotel);
        HotelJpaEntity saved = jpaRepository.save(entity);
        return HotelJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<Hotel> findById(HotelId id) {
        return jpaRepository.findById(id.value()).map(HotelJpaMapper::toDomain);
    }

    @Override
    public boolean existsById(HotelId id) {
        return jpaRepository.existsById(id.value());
    }
}

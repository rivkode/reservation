package com.reservation.rate.infrastructure.persistence.repository;

import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.RateId;
import com.reservation.rate.domain.model.RoomTypeId;
import com.reservation.rate.domain.model.RoomTypeRate;
import com.reservation.rate.domain.repository.RoomTypeRateRepository;
import com.reservation.rate.infrastructure.persistence.entity.RoomTypeRateJpaEntity;
import com.reservation.rate.infrastructure.persistence.mapper.RoomTypeRateJpaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomTypeRateRepositoryImpl implements RoomTypeRateRepository {

    private final RoomTypeRateJpaRepository jpaRepository;

    @Override
    public RoomTypeRate save(RoomTypeRate rate) {
        RoomTypeRateJpaEntity saved = jpaRepository.save(RoomTypeRateJpaMapper.toEntity(rate));
        return RoomTypeRateJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<RoomTypeRate> findById(RateId id) {
        return jpaRepository.findById(id.value()).map(RoomTypeRateJpaMapper::toDomain);
    }

    @Override
    public boolean existsByNaturalKey(HotelId hotelId, RoomTypeId roomTypeId, LocalDate date) {
        return jpaRepository.existsByHotelIdAndRoomTypeIdAndRateDate(
            hotelId.value(), roomTypeId.value(), date);
    }

    @Override
    public List<RoomTypeRate> findByRange(HotelId hotelId, RoomTypeId roomTypeId,
                                          LocalDate from, LocalDate to) {
        return jpaRepository.findByHotelIdAndRoomTypeIdAndRateDateBetweenOrderByRateDateAsc(
                hotelId.value(), roomTypeId.value(), from, to).stream()
            .map(RoomTypeRateJpaMapper::toDomain)
            .toList();
    }
}

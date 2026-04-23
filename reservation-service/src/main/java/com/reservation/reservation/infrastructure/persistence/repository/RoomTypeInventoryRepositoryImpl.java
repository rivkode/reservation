package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import com.reservation.reservation.infrastructure.persistence.entity.RoomTypeInventoryJpaEntity;
import com.reservation.reservation.infrastructure.persistence.entity.RoomTypeInventoryJpaEntity.InventoryPk;
import com.reservation.reservation.infrastructure.persistence.mapper.RoomTypeInventoryJpaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomTypeInventoryRepositoryImpl implements RoomTypeInventoryRepository {

    private final RoomTypeInventoryJpaRepository jpaRepository;

    @Override
    public Optional<RoomTypeInventory> findByKey(InventoryKey key) {
        InventoryPk pk = new InventoryPk(
            key.hotelId().value(), key.roomTypeId().value(), key.stayDate());
        return jpaRepository.findById(pk).map(RoomTypeInventoryJpaMapper::toDomain);
    }

    @Override
    public List<RoomTypeInventory> findRange(HotelId hotelId,
                                              RoomTypeId roomTypeId,
                                              LocalDate fromDate,
                                              LocalDate toDate) {
        List<RoomTypeInventoryJpaEntity> rows = jpaRepository.findRange(
            hotelId.value(), roomTypeId.value(), fromDate, toDate);
        return rows.stream().map(RoomTypeInventoryJpaMapper::toDomain).toList();
    }

    @Override
    public void save(RoomTypeInventory inventory) {
        jpaRepository.save(RoomTypeInventoryJpaMapper.toEntity(inventory));
    }

    @Override
    public void saveAll(List<RoomTypeInventory> inventories) {
        jpaRepository.saveAll(inventories.stream()
            .map(RoomTypeInventoryJpaMapper::toEntity)
            .toList());
    }
}

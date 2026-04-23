package com.reservation.reservation.infrastructure.persistence.mapper;

import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryCount;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.infrastructure.persistence.entity.RoomTypeInventoryJpaEntity;
import com.reservation.reservation.infrastructure.persistence.entity.RoomTypeInventoryJpaEntity.InventoryPk;

public final class RoomTypeInventoryJpaMapper {

    private RoomTypeInventoryJpaMapper() {
    }

    public static RoomTypeInventoryJpaEntity toEntity(RoomTypeInventory domain) {
        InventoryKey key = domain.key();
        return new RoomTypeInventoryJpaEntity(
            new InventoryPk(key.hotelId().value(), key.roomTypeId().value(), key.stayDate()),
            domain.totalRooms().value(),
            domain.availableRooms().value(),
            domain.version(),
            domain.createdAt(),
            domain.updatedAt()
        );
    }

    public static RoomTypeInventory toDomain(RoomTypeInventoryJpaEntity entity) {
        InventoryPk pk = entity.getId();
        return RoomTypeInventory.restore(
            new InventoryKey(
                HotelId.of(pk.getHotelId()),
                RoomTypeId.of(pk.getRoomTypeId()),
                pk.getStayDate()),
            InventoryCount.of(entity.getTotalRooms()),
            InventoryCount.of(entity.getAvailableRooms()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

package com.reservation.hotel.infrastructure.persistence.mapper;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.MaxOccupancy;
import com.reservation.hotel.domain.model.RoomType;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.domain.model.RoomTypeName;
import com.reservation.hotel.infrastructure.persistence.entity.RoomTypeJpaEntity;

public final class RoomTypeJpaMapper {

    private RoomTypeJpaMapper() {
    }

    public static RoomTypeJpaEntity toEntity(RoomType roomType) {
        return new RoomTypeJpaEntity(
            roomType.id().value(),
            roomType.hotelId().value(),
            roomType.name().value(),
            roomType.maxOccupancy().value(),
            roomType.version(),
            roomType.createdAt(),
            roomType.updatedAt()
        );
    }

    public static RoomType toDomain(RoomTypeJpaEntity entity) {
        return RoomType.restore(
            RoomTypeId.of(entity.getId()),
            HotelId.of(entity.getHotelId()),
            new RoomTypeName(entity.getName()),
            new MaxOccupancy(entity.getMaxOccupancy()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

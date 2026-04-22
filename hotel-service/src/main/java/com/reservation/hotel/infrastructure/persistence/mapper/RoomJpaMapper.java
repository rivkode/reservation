package com.reservation.hotel.infrastructure.persistence.mapper;

import com.reservation.hotel.domain.model.Floor;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.Room;
import com.reservation.hotel.domain.model.RoomId;
import com.reservation.hotel.domain.model.RoomNumber;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.infrastructure.persistence.entity.RoomJpaEntity;

public final class RoomJpaMapper {

    private RoomJpaMapper() {
    }

    public static RoomJpaEntity toEntity(Room room) {
        return new RoomJpaEntity(
            room.id().value(),
            room.hotelId().value(),
            room.roomTypeId().value(),
            room.floor().value(),
            room.number().value(),
            room.status(),
            room.version(),
            room.createdAt(),
            room.updatedAt()
        );
    }

    public static Room toDomain(RoomJpaEntity entity) {
        return Room.restore(
            RoomId.of(entity.getId()),
            HotelId.of(entity.getHotelId()),
            RoomTypeId.of(entity.getRoomTypeId()),
            new Floor(entity.getFloor()),
            new RoomNumber(entity.getNumber()),
            entity.getStatus(),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

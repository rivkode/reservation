package com.reservation.reservation.infrastructure.persistence.mapper;

import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.RoomAssignment;
import com.reservation.reservation.domain.model.RoomId;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.infrastructure.persistence.entity.RoomAssignmentJpaEntity;

public final class RoomAssignmentJpaMapper {

    private RoomAssignmentJpaMapper() {
    }

    public static RoomAssignmentJpaEntity toEntity(RoomAssignment domain) {
        return new RoomAssignmentJpaEntity(
            domain.roomId().value(),
            domain.hotelId().value(),
            domain.roomTypeId().value(),
            domain.version(),
            domain.createdAt(),
            domain.updatedAt()
        );
    }

    public static RoomAssignment toDomain(RoomAssignmentJpaEntity entity) {
        return RoomAssignment.restore(
            RoomId.of(entity.getRoomId()),
            HotelId.of(entity.getHotelId()),
            RoomTypeId.of(entity.getRoomTypeId()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

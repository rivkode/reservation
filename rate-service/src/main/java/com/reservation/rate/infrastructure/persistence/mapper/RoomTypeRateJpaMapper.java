package com.reservation.rate.infrastructure.persistence.mapper;

import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.Money;
import com.reservation.rate.domain.model.RateId;
import com.reservation.rate.domain.model.RoomTypeId;
import com.reservation.rate.domain.model.RoomTypeRate;
import com.reservation.rate.infrastructure.persistence.entity.RoomTypeRateJpaEntity;

public final class RoomTypeRateJpaMapper {

    private RoomTypeRateJpaMapper() {
    }

    public static RoomTypeRateJpaEntity toEntity(RoomTypeRate rate) {
        return new RoomTypeRateJpaEntity(
            rate.id().value(),
            rate.hotelId().value(),
            rate.roomTypeId().value(),
            rate.date(),
            rate.money().amount(),
            rate.money().currencyCode(),
            rate.version(),
            rate.createdAt(),
            rate.updatedAt()
        );
    }

    public static RoomTypeRate toDomain(RoomTypeRateJpaEntity entity) {
        return RoomTypeRate.restore(
            RateId.of(entity.getId()),
            HotelId.of(entity.getHotelId()),
            RoomTypeId.of(entity.getRoomTypeId()),
            entity.getRateDate(),
            Money.of(entity.getAmount(), entity.getCurrency()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

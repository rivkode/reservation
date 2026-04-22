package com.reservation.hotel.infrastructure.persistence.mapper;

import com.reservation.hotel.domain.model.Amenity;
import com.reservation.hotel.domain.model.Hotel;
import com.reservation.hotel.domain.model.HotelAddress;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.HotelName;
import com.reservation.hotel.domain.model.StarRating;
import com.reservation.hotel.infrastructure.persistence.entity.HotelJpaEntity;

import java.util.EnumSet;
import java.util.Set;

public final class HotelJpaMapper {

    private HotelJpaMapper() {
    }

    public static HotelJpaEntity toEntity(Hotel hotel) {
        return new HotelJpaEntity(
            hotel.id().value(),
            hotel.name().value(),
            hotel.address().street(),
            hotel.address().city(),
            hotel.address().country(),
            hotel.starRating().value(),
            hotel.amenities(),
            hotel.version(),
            hotel.createdAt(),
            hotel.updatedAt()
        );
    }

    public static Hotel toDomain(HotelJpaEntity entity) {
        Set<Amenity> amenities = entity.getAmenities() == null || entity.getAmenities().isEmpty()
            ? EnumSet.noneOf(Amenity.class)
            : EnumSet.copyOf(entity.getAmenities());
        return Hotel.restore(
            HotelId.of(entity.getId()),
            new HotelName(entity.getName()),
            new HotelAddress(entity.getAddressStreet(), entity.getAddressCity(), entity.getAddressCountry()),
            new StarRating(entity.getStarRating()),
            amenities,
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

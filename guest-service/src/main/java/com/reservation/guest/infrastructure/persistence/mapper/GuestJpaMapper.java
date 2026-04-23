package com.reservation.guest.infrastructure.persistence.mapper;

import com.reservation.guest.domain.model.Email;
import com.reservation.guest.domain.model.Guest;
import com.reservation.guest.domain.model.GuestId;
import com.reservation.guest.domain.model.GuestName;
import com.reservation.guest.domain.model.PhoneNumber;
import com.reservation.guest.infrastructure.persistence.entity.GuestJpaEntity;

public final class GuestJpaMapper {

    private GuestJpaMapper() {
    }

    public static GuestJpaEntity toEntity(Guest guest) {
        return new GuestJpaEntity(
            guest.id().value(),
            guest.name().firstName(),
            guest.name().lastName(),
            guest.email().value(),
            guest.phoneNumber().value(),
            guest.version(),
            guest.createdAt(),
            guest.updatedAt()
        );
    }

    public static Guest toDomain(GuestJpaEntity entity) {
        return Guest.restore(
            GuestId.of(entity.getId()),
            new GuestName(entity.getFirstName(), entity.getLastName()),
            new Email(entity.getEmail()),
            new PhoneNumber(entity.getPhoneNumber()),
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

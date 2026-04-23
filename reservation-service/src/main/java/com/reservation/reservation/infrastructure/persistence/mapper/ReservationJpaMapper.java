package com.reservation.reservation.infrastructure.persistence.mapper;

import com.reservation.reservation.domain.model.BillingQuote;
import com.reservation.reservation.domain.model.Cancellation;
import com.reservation.reservation.domain.model.CancellationOutcome;
import com.reservation.reservation.domain.model.GuestId;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.NumberOfGuests;
import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationId;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.StayPeriod;
import com.reservation.reservation.infrastructure.persistence.entity.ReservationJpaEntity;

public final class ReservationJpaMapper {

    private ReservationJpaMapper() {
    }

    public static ReservationJpaEntity toEntity(Reservation domain) {
        Cancellation cancellation = domain.cancellation().orElse(null);
        return new ReservationJpaEntity(
            domain.id().value(),
            domain.hotelId().value(),
            domain.roomTypeId().value(),
            domain.guestId().value(),
            domain.stayPeriod().checkIn(),
            domain.stayPeriod().checkOut(),
            domain.numberOfGuests().value(),
            domain.quote().total().amount(),
            domain.quote().total().currency(),
            domain.quote().quotedAt(),
            domain.status(),
            cancellation == null ? null : cancellation.cancelledAt(),
            cancellation == null ? null : cancellation.reason(),
            cancellation == null ? null : cancellation.outcome().refundRate(),
            cancellation == null ? null : cancellation.outcome().policyName(),
            domain.version(),
            domain.createdAt(),
            domain.updatedAt()
        );
    }

    public static Reservation toDomain(ReservationJpaEntity entity) {
        Cancellation cancellation = entity.getCancelledAt() == null
            ? null
            : new Cancellation(
                entity.getCancelledAt(),
                entity.getCancellationReason(),
                new CancellationOutcome(entity.getRefundRate(), entity.getCancellationPolicyName()));
        return Reservation.restore(
            ReservationId.of(entity.getId()),
            HotelId.of(entity.getHotelId()),
            RoomTypeId.of(entity.getRoomTypeId()),
            GuestId.of(entity.getGuestId()),
            new StayPeriod(entity.getCheckInDate(), entity.getCheckOutDate()),
            NumberOfGuests.of(entity.getNumberOfGuests()),
            new BillingQuote(Money.of(entity.getTotalAmount(), entity.getCurrency()),
                entity.getQuotedAt()),
            entity.getStatus(),
            cancellation,
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

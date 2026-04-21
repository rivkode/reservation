package com.reservation.contracts.event.hotel;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * hotel-service 에서 Room 이 삭제될 때 발행 (FR-H-05).
 * reservation-service 는 해당 RoomType 의 Inventory 레코드를 제거한다.
 */
public record RoomDeletedEvent(
    UUID eventId,
    Instant occurredAt,
    String hotelId,
    String roomId,
    String roomTypeId
) implements DomainEvent {

    public RoomDeletedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

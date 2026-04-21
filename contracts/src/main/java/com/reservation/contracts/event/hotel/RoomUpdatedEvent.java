package com.reservation.contracts.event.hotel;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * hotel-service 에서 Room 이 갱신될 때 발행 (FR-H-04). 구독자 입장에서
 * 의미 있는 변경은 RoomType 의 교체 정도이므로 집계 식별자만 포함한다.
 * floor/number 같은 hotel-service 내부 속성은 의도적으로 제외한다.
 */
public record RoomUpdatedEvent(
    UUID eventId,
    Instant occurredAt,
    String hotelId,
    String roomId,
    String roomTypeId
) implements DomainEvent {

    public RoomUpdatedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

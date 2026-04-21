package com.reservation.contracts.event.hotel;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * hotel-service 에서 Room 이 신규 생성될 때 {@code hotel-events} 토픽으로 발행.
 * reservation-service 가 구독해 해당 Room 의 {@code RoomTypeInventory} 레코드를
 * 초기화한다 (FR-RSV-04).
 *
 * <p>Room 의 물리 속성(floor, number 등)은 구독자에게 불필요하므로 의도적으로
 * 제외하고 집계 식별자({@code hotelId}, {@code roomId}, {@code roomTypeId})만 전달한다.
 */
public record RoomCreatedEvent(
    UUID eventId,
    Instant occurredAt,
    String hotelId,
    String roomId,
    String roomTypeId
) implements DomainEvent {

    public RoomCreatedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

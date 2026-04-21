package com.reservation.contracts.event.reservation;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * reservation-service 가 예약 생성 로컬 트랜잭션 성공 시 Outbox 를 거쳐
 * {@code reservation-events} 로 발행 (FR-RSV-02).
 *
 * <p>구독자:
 * <ul>
 *   <li>hotel-service — {@code RoomAvailabilityView} Redis 캐시 감소</li>
 *   <li>rate-service — Billing 레코드 생성</li>
 *   <li>guest-service — 방문 이력 갱신</li>
 * </ul>
 */
public record ReservationCreatedEvent(
    UUID eventId,
    Instant occurredAt,
    String reservationId,
    String hotelId,
    String roomTypeId,
    String guestId,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int numberOfGuests
) implements DomainEvent {

    public ReservationCreatedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

package com.reservation.contracts.event.reservation;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * reservation-service 가 예약 취소 로컬 트랜잭션(재고 복원 포함) 성공 시
 * Outbox 를 거쳐 발행 (FR-RSV-03).
 *
 * <p>구독자: hotel-service(캐시 복원), rate-service(Billing 취소),
 * guest-service(이력 롤백). PRD §11 Service Impact 매트릭스 보정본 참조.
 */
public record ReservationCancelledEvent(
    UUID eventId,
    Instant occurredAt,
    String reservationId,
    String hotelId,
    String roomTypeId,
    LocalDate checkInDate,
    LocalDate checkOutDate
) implements DomainEvent {

    public ReservationCancelledEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

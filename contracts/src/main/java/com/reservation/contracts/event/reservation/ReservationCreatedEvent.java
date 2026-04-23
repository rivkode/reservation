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
 *   <li>rate-service — Billing 레코드 생성 ({@code totalAmount}/{@code currency} 사용)</li>
 *   <li>guest-service — 방문 이력 갱신</li>
 * </ul>
 *
 * <p>{@code totalAmount} · {@code currency} 는 예약 생성 시점에 reservation-service 가
 * rate-service gRPC 로 견적해 합산한 "예약 시점에 합의된 총액" 의 스냅샷이다 — 이후
 * rate 가 변경되어도 본 예약의 청구액은 본 필드로 고정된다 (ddd-architect H1, ADR 0003).
 * 통화 최소 단위 정수(KRW: 원), ISO-4217 코드.
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
    int numberOfGuests,
    long totalAmount,
    String currency
) implements DomainEvent {

    public ReservationCreatedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(currency, "currency");
        if (totalAmount < 0) {
            throw new IllegalArgumentException("totalAmount must be non-negative, was " + totalAmount);
        }
    }
}

package com.reservation.contracts.event.billing;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * rate-service 가 {@code ReservationCreated} 를 구독해 Billing 을 생성한 뒤
 * {@code billing-events} 토픽으로 발행. reservation-service 가 구독해 예약
 * 상태를 확정 단계로 진행한다 (Saga 정상 경로, PRD §9.1).
 */
public record BillingCreatedEvent(
    UUID eventId,
    Instant occurredAt,
    String reservationId,
    String billingId,
    // 통화 최소 단위 (예: KRW 는 원 단위 정수)
    long totalAmount,
    // ISO-4217
    String currency
) implements DomainEvent {

    public BillingCreatedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

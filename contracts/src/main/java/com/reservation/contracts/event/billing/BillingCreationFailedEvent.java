package com.reservation.contracts.event.billing;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * rate-service 가 Billing 생성에 실패했을 때 발행. reservation-service 가 구독해
 * 예약을 자동 취소한다 (Saga 보상 트랜잭션, PRD §9.1).
 * {@code reason} 은 관찰·로깅 용이며 구독자는 실패 사유를 가지고 분기하지 않는다.
 */
public record BillingCreationFailedEvent(
    UUID eventId,
    Instant occurredAt,
    String reservationId,
    String reason
) implements DomainEvent {

    public BillingCreationFailedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

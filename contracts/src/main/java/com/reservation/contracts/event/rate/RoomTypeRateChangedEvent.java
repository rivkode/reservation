package com.reservation.contracts.event.rate;

import com.reservation.contracts.event.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * rate-service 에서 특정 객실 타입·날짜의 요금이 변경될 때 {@code rate-events}
 * 토픽으로 발행 (FR-R-03). 향후 billing/정산 서비스가 구독한다.
 */
public record RoomTypeRateChangedEvent(
    UUID eventId,
    Instant occurredAt,
    String hotelId,
    String roomTypeId,
    LocalDate date,
    // 통화 최소 단위 (예: KRW 는 원 단위 정수)
    long amount,
    // ISO-4217
    String currency
) implements DomainEvent {

    public RoomTypeRateChangedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

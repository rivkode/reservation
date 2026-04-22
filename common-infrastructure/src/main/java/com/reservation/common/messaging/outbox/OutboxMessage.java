package com.reservation.common.messaging.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbox 테이블에 저장되는 단일 메시지.
 *
 * <p>Producer (Application service) 는 로컬 트랜잭션 안에서 {@link OutboxRepository#save}
 * 로 저장하고, 별도 스케줄러인 {@link OutboxRelay} 가 {@code publishedAt == null} 인 행을
 * 주기 poll 해 Kafka 로 발행한 뒤 {@link OutboxRepository#markPublished} 로 시각을 갱신한다.
 * 이 구조가 ADR 0003 의 "로컬 트랜잭션 + at-least-once Outbox" 계약을 만족한다.
 *
 * <p>Kafka 발행 시점에 ADR 0001 §1 의 3 개 헤더 (`event-type` · `event-id` · `occurred-at`)
 * 가 relay 에서 채워진다. record 의 {@code id} 는 outbox row 자체의 식별자(UUID v7) 이고,
 * {@code eventId} 는 구독자 측 멱등성 키 (DomainEvent.eventId) 다.
 */
public record OutboxMessage(
    UUID id,
    String topic,
    String eventType,
    UUID eventId,
    Instant occurredAt,
    String partitionKey,
    byte[] payload,
    Instant createdAt,
    Instant publishedAt
) {

    public OutboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(partitionKey, "partitionKey");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt");
        // publishedAt 은 null 허용 (unpublished 상태 의미)
    }

    /** 아직 Kafka 로 발행되지 않은 상태인지 여부. */
    public boolean isUnpublished() {
        return publishedAt == null;
    }
}

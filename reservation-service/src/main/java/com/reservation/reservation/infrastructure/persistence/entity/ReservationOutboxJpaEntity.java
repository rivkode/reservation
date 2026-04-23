package com.reservation.reservation.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * reservation-service 로컬 Outbox 테이블 매핑. {@code reservation_outbox} 한 행이
 * 하나의 Kafka 메시지를 예약한다 (ADR 0003).
 *
 * <p>스키마/인덱스 정의는 V1 Flyway 마이그레이션 참조. hotel-service 와 동일 구조이며
 * 후속 PR (다른 서비스에 outbox 가 늘어나면) 에서 공통화 검토.
 */
@Entity
@Table(name = "reservation_outbox")
public class ReservationOutboxJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID id;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_id", nullable = false, columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "partition_key", nullable = false, length = 128)
    private String partitionKey;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ReservationOutboxJpaEntity() {
    }

    public ReservationOutboxJpaEntity(UUID id, String topic, String eventType, UUID eventId,
                                       Instant occurredAt, String partitionKey, byte[] payload,
                                       Instant createdAt, Instant publishedAt) {
        this.id = id;
        this.topic = topic;
        this.eventType = eventType;
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public byte[] getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}

package com.reservation.hotel.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer 멱등성 가드용 이벤트 처리 기록. PR-3.1 범위에서는 hotel-service 가
 * 소비하는 reservation-events 뿐이어서 consumer 그룹 구분 컬럼은 두지 않는다. 다른
 * 토픽 구독이 추가되어도 {@code eventId} 가 UUID 전역 고유이므로 테이블 하나로 충분.
 *
 * <p>reservation-service 의 동일 이름 엔티티와 의도적으로 중복 — 서비스간 Entity 공유
 * 금지 원칙에 따라 각 서비스가 자기 모듈 안에 소유한다.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {
    }

    public ProcessedEventJpaEntity(UUID eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}

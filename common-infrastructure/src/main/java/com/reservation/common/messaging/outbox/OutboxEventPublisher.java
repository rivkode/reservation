package com.reservation.common.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.common.domain.UuidV7;
import com.reservation.contracts.event.DomainEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Application Service 가 호출하는 Outbox 진입점.
 *
 * <p>{@link DomainEvent} 를 JSON 직렬화해 {@link OutboxRepository} 에 저장한다.
 * 호출자는 반드시 로컬 DB 트랜잭션 안에서 호출해 비즈니스 상태 변경과 Outbox
 * 적재가 원자적으로 커밋되게 한다 (ADR 0003).
 *
 * <p>Kafka 실 발행은 별도 스케줄러인 {@link OutboxRelay} 가 담당.
 */
public class OutboxEventPublisher {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventPublisher(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 도메인 이벤트를 Outbox 에 저장한다.
     *
     * @param event        직렬화할 이벤트 record
     * @param topic        대상 Kafka topic (예: {@code hotel-events})
     * @param partitionKey Kafka partition key (Plan Q4 결정: hotelId · reservationId 등)
     */
    public void publish(DomainEvent event, String topic, String partitionKey) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(partitionKey, "partitionKey");

        byte[] payload = serialise(event);
        OutboxMessage message = new OutboxMessage(
            UuidV7.create(),
            topic,
            event.getClass().getSimpleName(),
            event.eventId(),
            event.occurredAt(),
            partitionKey,
            payload,
            Instant.now(clock),
            null
        );
        repository.save(message);
    }

    private byte[] serialise(DomainEvent event) {
        try {
            return objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Failed to serialise DomainEvent " + event.getClass().getSimpleName()
                    + " (eventId=" + event.eventId() + ")", e);
        }
    }
}

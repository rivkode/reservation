package com.reservation.common.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.common.domain.UuidV7;
import com.reservation.contracts.event.DomainEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Application Service 가 호출하는 Outbox 진입점.
 *
 * <p>{@link DomainEvent} 를 JSON 직렬화해 {@link OutboxRepository} 에 저장한다.
 * {@link #publish} 에 {@code Propagation.MANDATORY} 를 적용해 반드시 상위
 * {@code @Transactional} 안에서 호출되어야 함을 런타임에 강제한다 — 비즈니스
 * 상태 변경과 Outbox 적재가 같은 로컬 트랜잭션에서 원자적으로 커밋되어야 한다는
 * ADR 0003 계약을 개발 타임에 즉시 드러내기 위함. 각 서비스별
 * {@link OutboxRepository} 구현이 이 계약을 반복 구현하지 않도록 공용 진입점에서
 * 한 번만 선언한다.
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
    @Transactional(propagation = Propagation.MANDATORY)
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

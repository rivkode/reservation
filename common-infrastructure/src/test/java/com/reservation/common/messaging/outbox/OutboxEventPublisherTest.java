package com.reservation.common.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reservation.contracts.event.DomainEvent;
import com.reservation.contracts.event.hotel.RoomCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-04-22T10:00:00Z");

    private OutboxRepository repository;
    private ObjectMapper objectMapper;
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        publisher = new OutboxEventPublisher(repository, objectMapper, clock);
    }

    @Test
    @DisplayName("publish 는 DomainEvent 를 Outbox 에 저장하고 메타데이터를 채운다")
    void publishStoresMessageWithDomainEventMetadata() {
        UUID eventId = UUID.fromString("018f4a9b-2c4d-7c34-8e9a-000000000010");
        Instant occurredAt = Instant.parse("2026-04-22T09:59:00Z");
        RoomCreatedEvent event = new RoomCreatedEvent(
            eventId, occurredAt, "H-1", "R-1", "RT-1"
        );

        publisher.publish(event, "hotel-events", "H-1");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).save(captor.capture());
        OutboxMessage saved = captor.getValue();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.topic()).isEqualTo("hotel-events");
        assertThat(saved.eventType()).isEqualTo("RoomCreatedEvent");
        assertThat(saved.eventId()).isEqualTo(eventId);
        assertThat(saved.occurredAt()).isEqualTo(occurredAt);
        assertThat(saved.partitionKey()).isEqualTo("H-1");
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.publishedAt()).isNull();
        assertThat(new String(saved.payload())).contains("\"hotelId\":\"H-1\"", "\"roomId\":\"R-1\"");
    }

    @Test
    @DisplayName("publish 인자가 null 이면 NPE 로 빠르게 실패한다")
    void publishRejectsNullArguments() {
        RoomCreatedEvent event = new RoomCreatedEvent(
            UUID.randomUUID(), NOW, "H-1", "R-1", "RT-1"
        );

        assertThatNpe(() -> publisher.publish(null, "hotel-events", "H-1"), "event");
        assertThatNpe(() -> publisher.publish(event, null, "H-1"), "topic");
        assertThatNpe(() -> publisher.publish(event, "hotel-events", null), "partitionKey");
    }

    @Test
    @DisplayName("ObjectMapper 직렬화 실패 시 IllegalStateException 으로 래핑되고 save 는 호출되지 않는다")
    void wrapsJsonProcessingExceptionAsIllegalStateAndSkipsSave() throws JsonProcessingException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        JsonProcessingException cause = new JsonProcessingException("boom") {};
        when(failingMapper.writeValueAsBytes(any(DomainEvent.class))).thenThrow(cause);

        OutboxEventPublisher failingPublisher = new OutboxEventPublisher(
            repository, failingMapper, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        UUID eventId = UUID.fromString("018f4a9b-2c4d-7c34-8e9a-000000000030");
        RoomCreatedEvent event = new RoomCreatedEvent(eventId, NOW, "H-1", "R-1", "RT-1");

        assertThatThrownBy(() -> failingPublisher.publish(event, "hotel-events", "H-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RoomCreatedEvent")
            .hasMessageContaining(eventId.toString())
            .hasCause(cause);

        verify(repository, never()).save(any(OutboxMessage.class));
    }

    private static void assertThatNpe(Runnable r, String expectedMessageFragment) {
        assertThatThrownBy(r::run)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(expectedMessageFragment);
    }
}

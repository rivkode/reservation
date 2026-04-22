package com.reservation.common.messaging.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-04-22T10:00:00Z");

    private OutboxRepository repository;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        relay = new OutboxRelay(repository, kafkaTemplate, clock, 100, 5_000L);
    }

    @Test
    @DisplayName("미발행 메시지를 Kafka 로 전송하고 publishedAt 을 현재 시각으로 기록한다")
    void publishesPendingMessagesAndMarksThemPublished() {
        OutboxMessage message = newMessage();
        when(repository.findUnpublished(100)).thenReturn(List.of(message));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, byte[]> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("hotel-events");
        assertThat(record.key()).isEqualTo("H-1");
        assertThat(record.value()).isEqualTo(message.payload());

        assertThat(headerValue(record, OutboxRelay.HEADER_EVENT_TYPE)).isEqualTo("RoomCreatedEvent");
        assertThat(headerValue(record, OutboxRelay.HEADER_EVENT_ID)).isEqualTo(message.eventId().toString());
        assertThat(headerValue(record, OutboxRelay.HEADER_OCCURRED_AT)).isEqualTo(message.occurredAt().toString());

        verify(repository).markPublished(eq(message.id()), eq(NOW));
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 markPublished 를 호출하지 않아 다음 tick 에 재시도 가능")
    void doesNotMarkPublishedOnKafkaFailure() {
        OutboxMessage message = newMessage();
        when(repository.findUnpublished(100)).thenReturn(List.of(message));
        CompletableFuture<SendResult<String, byte[]>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        relay.publishPending();

        verify(kafkaTemplate).send(any(ProducerRecord.class));
        verify(repository, never()).markPublished(any(UUID.class), any(Instant.class));
    }

    @Test
    @DisplayName("미발행 batch 가 비어 있으면 Kafka 호출이 일어나지 않는다")
    void skipsKafkaCallWhenNoPendingMessages() {
        when(repository.findUnpublished(100)).thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(repository, never()).markPublished(any(UUID.class), any(Instant.class));
    }

    @Test
    @DisplayName("batch 중 한 메시지 전송이 실패해도 나머지 메시지는 계속 발행된다")
    void continuesProcessingAfterPerMessageFailure() {
        OutboxMessage m1 = newMessage("018f4a9b-2c4d-7c34-8e9a-00000000002a");
        OutboxMessage m2 = newMessage("018f4a9b-2c4d-7c34-8e9a-00000000002b");
        when(repository.findUnpublished(100)).thenReturn(List.of(m1, m2));

        CompletableFuture<SendResult<String, byte[]>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
            .thenReturn(failed)
            .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
        verify(repository, never()).markPublished(eq(m1.id()), any(Instant.class));
        verify(repository).markPublished(eq(m2.id()), eq(NOW));
    }

    @Test
    @DisplayName("batchSize · sendTimeoutMs 가 0 이하면 생성 시 IAE 로 빠르게 실패한다")
    void rejectsNonPositiveConstructorArgs() {
        Clock clock = Clock.systemUTC();
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new OutboxRelay(repository, kafkaTemplate, clock, 0, 5_000L)
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batchSize");

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new OutboxRelay(repository, kafkaTemplate, clock, 100, 0L)
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sendTimeoutMs");
    }

    private OutboxMessage newMessage(String idHex) {
        return new OutboxMessage(
            UUID.fromString(idHex),
            "hotel-events",
            "RoomCreatedEvent",
            UUID.fromString("018f4a9b-2c4d-7c34-8e9a-000000000021"),
            Instant.parse("2026-04-22T09:59:00Z"),
            "H-1",
            "{\"hotelId\":\"H-1\"}".getBytes(StandardCharsets.UTF_8),
            Instant.parse("2026-04-22T09:59:30Z"),
            null
        );
    }

    private OutboxMessage newMessage() {
        return newMessage("018f4a9b-2c4d-7c34-8e9a-000000000020");
    }

    private static String headerValue(ProducerRecord<?, ?> record, String key) {
        return new String(record.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
    }
}

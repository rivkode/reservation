package com.reservation.hotel.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reservation.contracts.event.reservation.ReservationCancelledEvent;
import com.reservation.contracts.event.reservation.ReservationCreatedEvent;
import com.reservation.hotel.application.service.ReservationEventApplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ReservationEventsKafkaConsumer 디스패치 단위 테스트")
class ReservationEventsKafkaConsumerTest {

    private ReservationEventApplicationService applicationService;
    /**
     * Consumer 내부 ObjectMapper 와 payload 직렬화용 ObjectMapper 를 의도적으로 분리한다.
     * 동일 인스턴스를 공유하면 Consumer 가 JavaTimeModule 등록을 실수로 빼뜨려도 테스트가
     * 통과하는 가짜 초록 상태가 된다. 각 테스트가 독립적으로 바이트를 만들어 실제 운영
     * Consumer 에 전달된 payload 와 같은 상황을 재현한다.
     */
    private ObjectMapper producerMapper;
    private ObjectMapper consumerMapper;
    private ReservationEventsKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        applicationService = mock(ReservationEventApplicationService.class);
        producerMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumerMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new ReservationEventsKafkaConsumer(applicationService, consumerMapper);
    }

    @Test
    @DisplayName("event-type=ReservationCreatedEvent → onReservationCreated 호출")
    void dispatches_reservation_created() throws Exception {
        ReservationCreatedEvent event = new ReservationCreatedEvent(
            UUID.randomUUID(),
            Instant.parse("2026-06-01T10:00:00Z"),
            "R-20260601-001",
            "01933333-1111-7aaa-9aaa-111122223333",
            "01933333-2222-7aaa-9aaa-111122223333",
            "01933333-3333-7aaa-9aaa-111122223333",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 3),
            2,
            300_000L,
            "KRW");
        byte[] payload = producerMapper.writeValueAsBytes(event);

        consumer.consume(recordWithHeader("ReservationCreatedEvent", payload));

        verify(applicationService).onReservationCreated(any(ReservationCreatedEvent.class));
    }

    @Test
    @DisplayName("event-type=ReservationCancelledEvent → onReservationCancelled 호출")
    void dispatches_reservation_cancelled() throws Exception {
        ReservationCancelledEvent event = new ReservationCancelledEvent(
            UUID.randomUUID(),
            Instant.parse("2026-06-01T10:00:00Z"),
            "R-20260601-001",
            "01933333-1111-7aaa-9aaa-111122223333",
            "01933333-2222-7aaa-9aaa-111122223333",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 3));
        byte[] payload = producerMapper.writeValueAsBytes(event);

        consumer.consume(recordWithHeader("ReservationCancelledEvent", payload));

        verify(applicationService).onReservationCancelled(any(ReservationCancelledEvent.class));
    }

    @Test
    @DisplayName("event-type 헤더 누락 → skip + application 호출 없음")
    void skips_when_header_missing() {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
            ReservationEventsKafkaConsumer.TOPIC, 0, 0L, "key", new byte[]{1, 2, 3});

        consumer.consume(record);

        verify(applicationService, never()).onReservationCreated(any());
        verify(applicationService, never()).onReservationCancelled(any());
    }

    @Test
    @DisplayName("event-type 헤더 value 가 null → skip + application 호출 없음")
    void skips_when_header_value_null() {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
            ReservationEventsKafkaConsumer.TOPIC, 0, 0L, "key", new byte[]{1, 2, 3});
        record.headers().add(new RecordHeader(
            ReservationEventsKafkaConsumer.HEADER_EVENT_TYPE, (byte[]) null));

        consumer.consume(record);

        verify(applicationService, never()).onReservationCreated(any());
        verify(applicationService, never()).onReservationCancelled(any());
    }

    @Test
    @DisplayName("알 수 없는 event-type → skip + application 호출 없음")
    void skips_unknown_type() {
        consumer.consume(recordWithHeader("UnknownEvent", new byte[]{1, 2, 3}));

        verify(applicationService, never()).onReservationCreated(any());
        verify(applicationService, never()).onReservationCancelled(any());
    }

    @Test
    @DisplayName("JSON 파싱 실패 → IllegalStateException (DefaultErrorHandler 위임)")
    void throws_on_parse_failure() {
        byte[] broken = "not-json".getBytes(StandardCharsets.UTF_8);
        ConsumerRecord<String, byte[]> record = recordWithHeader("ReservationCreatedEvent", broken);

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> consumer.consume(record))
            .withMessageContaining("deserialisation");
    }

    @Test
    @DisplayName("헤더 상수 노출 — Producer 와의 계약 문서화")
    void header_constant_value() {
        assertThat(ReservationEventsKafkaConsumer.HEADER_EVENT_TYPE).isEqualTo("event-type");
        assertThat(ReservationEventsKafkaConsumer.TOPIC).isEqualTo("reservation-events");
    }

    private ConsumerRecord<String, byte[]> recordWithHeader(String eventType, byte[] payload) {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
            ReservationEventsKafkaConsumer.TOPIC, 0, 0L, "key", payload);
        record.headers().add(new RecordHeader(
            ReservationEventsKafkaConsumer.HEADER_EVENT_TYPE,
            eventType.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}

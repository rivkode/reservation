package com.reservation.reservation.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.reservation.contracts.event.hotel.RoomCreatedEvent;
import com.reservation.contracts.event.hotel.RoomDeletedEvent;
import com.reservation.contracts.event.hotel.RoomUpdatedEvent;
import com.reservation.reservation.application.service.HotelEventApplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("HotelEventsKafkaConsumer 디스패치 단위 테스트")
class HotelEventsKafkaConsumerTest {

    private HotelEventApplicationService applicationService;
    /**
     * Consumer 내부 ObjectMapper 와 payload 직렬화용 ObjectMapper 를 의도적으로 분리한다.
     * 동일 인스턴스를 공유하면 Consumer 가 JavaTimeModule 등록을 실수로 빼뜨려도 테스트가
     * 통과하는 가짜 초록 상태가 된다. 여기선 각 테스트가 독립적으로 바이트를 만들어 실제
     * 운영 Consumer 에 전달된 payload 와 같은 상황을 재현한다.
     */
    private ObjectMapper producerMapper;
    private ObjectMapper consumerMapper;
    private HotelEventsKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        applicationService = mock(HotelEventApplicationService.class);
        producerMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumerMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new HotelEventsKafkaConsumer(applicationService, consumerMapper);
    }

    @Test
    @DisplayName("event-type=RoomCreatedEvent → onRoomCreated 호출")
    void dispatches_room_created() throws Exception {
        RoomCreatedEvent event = new RoomCreatedEvent(
            UUID.randomUUID(), Instant.parse("2026-04-23T10:00:00Z"),
            "01933333-1111-7aaa-9aaa-111122223333",
            "01933333-3333-7aaa-9aaa-111122223333",
            "01933333-aaaa-7aaa-9aaa-111122223333");
        byte[] payload = producerMapper.writeValueAsBytes(event);

        consumer.consume(recordWithHeader("RoomCreatedEvent", payload));

        verify(applicationService).onRoomCreated(any(RoomCreatedEvent.class));
    }

    @Test
    @DisplayName("event-type=RoomUpdatedEvent → onRoomUpdated 호출")
    void dispatches_room_updated() throws Exception {
        RoomUpdatedEvent event = new RoomUpdatedEvent(
            UUID.randomUUID(), Instant.parse("2026-04-23T10:00:00Z"),
            "01933333-1111-7aaa-9aaa-111122223333",
            "01933333-3333-7aaa-9aaa-111122223333",
            "01933333-bbbb-7aaa-9aaa-111122223333");
        byte[] payload = producerMapper.writeValueAsBytes(event);

        consumer.consume(recordWithHeader("RoomUpdatedEvent", payload));

        verify(applicationService).onRoomUpdated(any(RoomUpdatedEvent.class));
    }

    @Test
    @DisplayName("event-type=RoomDeletedEvent → onRoomDeleted 호출")
    void dispatches_room_deleted() throws Exception {
        RoomDeletedEvent event = new RoomDeletedEvent(
            UUID.randomUUID(), Instant.parse("2026-04-23T10:00:00Z"),
            "01933333-1111-7aaa-9aaa-111122223333",
            "01933333-3333-7aaa-9aaa-111122223333",
            "01933333-aaaa-7aaa-9aaa-111122223333");
        byte[] payload = producerMapper.writeValueAsBytes(event);

        consumer.consume(recordWithHeader("RoomDeletedEvent", payload));

        verify(applicationService).onRoomDeleted(any(RoomDeletedEvent.class));
    }

    @Test
    @DisplayName("event-type 헤더 누락 → skip + application 호출 없음")
    void skips_when_header_missing() {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
            HotelEventsKafkaConsumer.TOPIC, 0, 0L, "key", new byte[]{1, 2, 3});

        consumer.consume(record);

        verify(applicationService, never()).onRoomCreated(any());
        verify(applicationService, never()).onRoomUpdated(any());
        verify(applicationService, never()).onRoomDeleted(any());
    }

    @Test
    @DisplayName("알 수 없는 event-type → skip + application 호출 없음")
    void skips_unknown_type() {
        consumer.consume(recordWithHeader("UnknownEvent", new byte[]{1, 2, 3}));

        verify(applicationService, never()).onRoomCreated(any());
        verify(applicationService, never()).onRoomUpdated(any());
        verify(applicationService, never()).onRoomDeleted(any());
    }

    @Test
    @DisplayName("JSON 파싱 실패 → IllegalStateException (DefaultErrorHandler 위임)")
    void throws_on_parse_failure() {
        byte[] broken = "not-json".getBytes(StandardCharsets.UTF_8);
        ConsumerRecord<String, byte[]> record = recordWithHeader("RoomCreatedEvent", broken);

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> consumer.consume(record))
            .withMessageContaining("deserialisation");
    }

    @Test
    @DisplayName("헤더 상수 노출 — Producer 와의 계약 문서화")
    void header_constant_value() {
        assertThat(HotelEventsKafkaConsumer.HEADER_EVENT_TYPE).isEqualTo("event-type");
        assertThat(HotelEventsKafkaConsumer.TOPIC).isEqualTo("hotel-events");
    }

    private ConsumerRecord<String, byte[]> recordWithHeader(String eventType, byte[] payload) {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
            HotelEventsKafkaConsumer.TOPIC, 0, 0L, "key", payload);
        record.headers().add(new RecordHeader(
            HotelEventsKafkaConsumer.HEADER_EVENT_TYPE,
            eventType.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}

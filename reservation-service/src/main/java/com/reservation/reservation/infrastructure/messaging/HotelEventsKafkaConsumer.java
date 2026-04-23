package com.reservation.reservation.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.contracts.event.hotel.RoomCreatedEvent;
import com.reservation.contracts.event.hotel.RoomDeletedEvent;
import com.reservation.contracts.event.hotel.RoomUpdatedEvent;
import com.reservation.reservation.application.service.HotelEventApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * hotel-events 토픽 Kafka consumer.
 *
 * <p>Outbox relay (common-infrastructure {@code OutboxRelay}) 가 발행 시 붙이는
 * {@code event-type} 헤더를 기준으로 구체 이벤트 타입을 결정하고 JSON 을 역직렬화한다.
 * contracts 의 이벤트 record 는 다형 직렬화/역직렬화를 위한 `@JsonTypeInfo` 등을 쓰지 않아
 * 헤더 기반 디스패치가 필수적이다.
 *
 * <p>트랜잭션 경계는 {@link HotelEventApplicationService} 쪽 {@code @Transactional} 이 담당.
 * 본 consumer 는 메시지 파싱 · 애플리케이션 호출만 한다. Phase 1 한계로 실패 시 DLQ 토픽은
 * 미구현이며, Spring Kafka 기본 {@code DefaultErrorHandler} 의 유한 retry + 에러 로그로만
 * 대응한다. 정식 DLQ 는 PR-4.x 안정화 단계에서 도입한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelEventsKafkaConsumer {

    static final String HEADER_EVENT_TYPE = "event-type";
    static final String TOPIC = "hotel-events";

    private final HotelEventApplicationService applicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = TOPIC,
        groupId = "${app.kafka.consumer.hotel-events.group-id:reservation-service.hotel-events}",
        containerFactory = "hotelEventsListenerContainerFactory",
        autoStartup = "${app.kafka.listener.auto-startup:true}"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        String eventType = readHeader(record);
        if (eventType == null) {
            log.error("hotel-events message missing '{}' header — skipping. offset={} partition={}",
                HEADER_EVENT_TYPE, record.offset(), record.partition());
            return;
        }

        try {
            switch (eventType) {
                case "RoomCreatedEvent" -> applicationService.onRoomCreated(
                    objectMapper.readValue(record.value(), RoomCreatedEvent.class));
                case "RoomUpdatedEvent" -> applicationService.onRoomUpdated(
                    objectMapper.readValue(record.value(), RoomUpdatedEvent.class));
                case "RoomDeletedEvent" -> applicationService.onRoomDeleted(
                    objectMapper.readValue(record.value(), RoomDeletedEvent.class));
                default -> log.warn("Unknown hotel-events type '{}' — skipping. offset={}",
                    eventType, record.offset());
            }
        } catch (IOException e) {
            // JSON 파싱 실패는 DLQ 가 없는 현 단계에서는 error 로그만 남기고 re-throw.
            // DefaultErrorHandler 의 유한 retry 후 seek 을 건너뛰어 다음 오프셋으로 진행.
            log.error("Failed to deserialise hotel-events type={} offset={}: {}",
                eventType, record.offset(), e.getMessage(), e);
            throw new IllegalStateException("hotel-events deserialisation failed", e);
        }
    }

    private String readHeader(ConsumerRecord<String, byte[]> record) {
        var header = record.headers().lastHeader(HEADER_EVENT_TYPE);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}

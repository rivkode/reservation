package com.reservation.hotel.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.contracts.event.reservation.ReservationCancelledEvent;
import com.reservation.contracts.event.reservation.ReservationCreatedEvent;
import com.reservation.hotel.application.service.ReservationEventApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * reservation-events 토픽 Kafka consumer. FR-H-07 — Redis {@code RoomAvailabilityView}
 * 갱신을 위해 reservation-service 의 {@link ReservationCreatedEvent} ·
 * {@link ReservationCancelledEvent} 를 수신한다.
 *
 * <p>Outbox relay (common-infrastructure {@code OutboxRelay}) 가 발행 시 붙이는
 * {@code event-type} 헤더를 기준으로 구체 이벤트 타입을 결정하고 JSON 을 역직렬화한다.
 * reservation-service 의 {@code HotelEventsKafkaConsumer} 와 동일 패턴이며, 추후 다른
 * 서비스에도 동형 consumer 가 생기면 common-infrastructure 로 승격을 고려한다.
 *
 * <p>트랜잭션 경계는 {@link ReservationEventApplicationService} 쪽 {@code @Transactional}
 * 이 담당. 본 consumer 는 메시지 파싱 · 애플리케이션 호출만 한다. Phase 1 한계로 실패 시
 * DLQ 토픽은 미구현이며, Spring Kafka 기본 {@code DefaultErrorHandler} 의 유한 retry +
 * 에러 로그로만 대응한다 (정식 DLQ 는 PR-4.x 안정화 단계).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventsKafkaConsumer {

    static final String HEADER_EVENT_TYPE = "event-type";
    static final String TOPIC = "reservation-events";

    private final ReservationEventApplicationService applicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = TOPIC,
        groupId = "${app.kafka.consumer.reservation-events.group-id:hotel-service.reservation-events}",
        containerFactory = "reservationEventsListenerContainerFactory",
        autoStartup = "${app.kafka.listener.auto-startup:true}"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        String eventType = readHeader(record);
        if (eventType == null) {
            log.error("reservation-events message missing '{}' header — skipping. offset={} partition={}",
                HEADER_EVENT_TYPE, record.offset(), record.partition());
            return;
        }

        try {
            switch (eventType) {
                case "ReservationCreatedEvent" -> applicationService.onReservationCreated(
                    objectMapper.readValue(record.value(), ReservationCreatedEvent.class));
                case "ReservationCancelledEvent" -> applicationService.onReservationCancelled(
                    objectMapper.readValue(record.value(), ReservationCancelledEvent.class));
                default -> log.warn("Unknown reservation-events type '{}' — skipping. offset={}",
                    eventType, record.offset());
            }
        } catch (IOException e) {
            log.error("Failed to deserialise reservation-events type={} offset={}: {}",
                eventType, record.offset(), e.getMessage(), e);
            throw new IllegalStateException("reservation-events deserialisation failed", e);
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

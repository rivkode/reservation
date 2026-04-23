package com.reservation.reservation.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.contracts.event.billing.BillingCreationFailedEvent;
import com.reservation.reservation.application.service.CancelReservationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * billing-events 토픽 Kafka consumer (Saga 보상 경로).
 *
 * <p>{@code event-type} 헤더 기반 디스패치는 {@link HotelEventsKafkaConsumer} 와 동일.
 * PR-2.3 범위는 {@code BillingCreationFailedEvent} 만 처리하며 {@code BillingCreatedEvent}
 * (정상 확정 단계) 는 본 PR 범위 외 — 헤더에 도착해도 debug 로그 후 스킵하고 오프셋은
 * 그대로 커밋된다. 후속 PR 가 같은 group-id 로 핸들러를 추가하면 본 PR 머지 이전 메시지는
 * 처리되지 않으므로 별도 group-id 또는 offset reset 정책을 후속 PR 에서 결정한다.
 *
 * <p>트랜잭션 경계는 {@link CancelReservationApplicationService#cancelOnBillingFailure} 의
 * {@code TransactionTemplate} 가 담당. 본 consumer 는 메시지 파싱 + 디스패치만 책임진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEventsKafkaConsumer {

    static final String HEADER_EVENT_TYPE = "event-type";
    static final String TOPIC = "billing-events";

    private final CancelReservationApplicationService cancelReservationApplicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = TOPIC,
        groupId = "${app.kafka.consumer.billing-events.group-id:reservation-service.billing-events}",
        containerFactory = "billingEventsListenerContainerFactory",
        autoStartup = "${app.kafka.listener.auto-startup:true}"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        String eventType = readHeader(record);
        if (eventType == null) {
            log.error("billing-events message missing '{}' header — skipping. offset={} partition={}",
                HEADER_EVENT_TYPE, record.offset(), record.partition());
            return;
        }

        try {
            switch (eventType) {
                case "BillingCreationFailedEvent" -> cancelReservationApplicationService
                    .cancelOnBillingFailure(
                        objectMapper.readValue(record.value(), BillingCreationFailedEvent.class));
                case "BillingCreatedEvent" -> log.debug(
                    "BillingCreatedEvent skipped (handler in follow-up PR). offset={}",
                    record.offset());
                default -> log.warn("Unknown billing-events type '{}' — skipping. offset={}",
                    eventType, record.offset());
            }
        } catch (IOException e) {
            log.error("Failed to deserialise billing-events type={} offset={}: {}",
                eventType, record.offset(), e.getMessage(), e);
            throw new IllegalStateException("billing-events deserialisation failed", e);
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

package com.reservation.common.messaging.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 테이블을 주기 poll 해 Kafka 로 발행 후 {@code publishedAt} 을 기록한다.
 *
 * <p>ADR 0001 §1 의 헤더 규약 (`event-type` · `event-id` · `occurred-at`) 을 본
 * 클래스에서 채워 전송한다. 전송 실패 시 {@code publishedAt} 을 갱신하지 않아
 * 다음 tick 에서 재시도 (at-least-once).
 *
 * <h3>Phase 1 한계</h3>
 * <ul>
 *   <li><strong>동기 순차 전송</strong>: batch 내 메시지를 {@code send().get(timeoutMs)}
 *       로 순차 처리한다. Kafka tail latency 가 높을 때 한 tick 이 최악
 *       {@code batchSize × timeoutMs} 까지 길어질 수 있다. Phase 2 개선 후보:
 *       {@code CompletableFuture.allOf(...).join()} · send 별도 executor ·
 *       spring-kafka ACK 비동기 수렴.</li>
 *   <li><strong>멀티 인스턴스 가드 없음</strong>: 본 relay 자체에는 분산 락이 없다.
 *       여러 app 인스턴스가 동시에 같은 outbox 행을 poll 하는 것을 막으려면
 *       {@link OutboxRepository#findUnpublished} 구현체가 {@code SELECT ...
 *       FOR UPDATE SKIP LOCKED} 또는 동등 전략을 반드시 써야 한다.</li>
 *   <li><strong>MDC 전파 미구현</strong>: 후속 PR (Kafka ProducerInterceptor 도입)
 *       에서 {@code LoggingMdcKeys.EVENT_ID} 등을 MDC 에 올려 소비자 측 로그와
 *       trace 를 엮을 예정. 현재는 파라미터 binding 로깅만.</li>
 * </ul>
 */
@Slf4j
public class OutboxRelay {

    static final String HEADER_EVENT_TYPE = "event-type";
    static final String HEADER_EVENT_ID = "event-id";
    static final String HEADER_OCCURRED_AT = "occurred-at";

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long DEFAULT_SEND_TIMEOUT_MS = 5_000L;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Clock clock;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, byte[]> kafkaTemplate,
                       Clock clock) {
        this(repository, kafkaTemplate, clock, DEFAULT_BATCH_SIZE, DEFAULT_SEND_TIMEOUT_MS);
    }

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, byte[]> kafkaTemplate,
                       Clock clock,
                       int batchSize,
                       long sendTimeoutMs) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        if (sendTimeoutMs <= 0) {
            throw new IllegalArgumentException("sendTimeoutMs must be positive, was " + sendTimeoutMs);
        }
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    public void publishPending() {
        List<OutboxMessage> batch = repository.findUnpublished(batchSize);
        for (OutboxMessage message : batch) {
            publishOne(message);
        }
    }

    private void publishOne(OutboxMessage message) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
            message.topic(), null, message.partitionKey(), message.payload()
        );
        record.headers().add(new RecordHeader(
            HEADER_EVENT_TYPE, message.eventType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(
            HEADER_EVENT_ID, message.eventId().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(
            HEADER_OCCURRED_AT, message.occurredAt().toString().getBytes(StandardCharsets.UTF_8)));

        // TODO: MDC.put(LoggingMdcKeys.EVENT_ID, message.eventId().toString()) 로 감싸 trace
        //       chain 에 묶을 것. Kafka ProducerInterceptor 도입과 함께 후속 PR 에서 처리.

        // Kafka 전송: send 성공 여부와 markPublished 의 실패를 분리해 관찰성을 확보한다.
        // send 가 실패하면 publishedAt 이 null 로 남아 다음 tick 에서 자연스럽게 재시도.
        try {
            kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing outbox message id={} eventType={} eventId={}",
                message.id(), message.eventType(), message.eventId(), ie);
            return;
        } catch (Exception e) {
            log.error("Kafka send failed for outbox message id={} eventType={} eventId={} — will retry",
                message.id(), message.eventType(), message.eventId(), e);
            return;
        }

        // send 는 성공. markPublished 가 실패하면 다음 tick 이 같은 메시지를 다시
        // 보내 consumer 측이 중복을 보게 된다. 구독자는 processed_events 테이블로
        // 멱등 보장하지만 비정상 상황임을 로그·메트릭으로 드러내 운영자가 감지한다.
        try {
            repository.markPublished(message.id(), Instant.now(clock));
        } catch (Exception e) {
            log.error("Kafka send succeeded but markPublished failed — consumer will see duplicate."
                    + " id={} eventType={} eventId={}",
                message.id(), message.eventType(), message.eventId(), e);
            // TODO: metric 'outbox.mark_published.failure' 계수화 (후속 PR 관측 스택 도입 시).
        }
    }
}

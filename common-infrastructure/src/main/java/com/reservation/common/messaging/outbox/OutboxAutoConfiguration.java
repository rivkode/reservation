package com.reservation.common.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Outbox 패턴 유틸(공용) 자동 구성.
 *
 * <p>소비 서비스가 (a) {@link OutboxRepository} 빈 (spring-data-jpa 구현) 과
 * (b) {@link KafkaTemplate} 빈 (spring-kafka 설정) 을 **둘 다** 제공할 때만 본
 * 자동구성이 {@link OutboxEventPublisher} 와 {@link OutboxRelay} 를 등록한다.
 * 한쪽만 있으면 두 빈 모두 등록되지 않는다 (publisher 만 살면 DB 쓰기는 되지만
 * 영원히 발행되지 않는 사일런트 실패를 피하기 위함).
 *
 * <p>{@code @EnableScheduling} 으로 relay 의 {@code @Scheduled} 를 활성화한다.
 * 소비 서비스가 별도 스케줄링 설정을 가질 경우 충돌 없이 병존한다.
 */
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, OutboxRepository.class})
@ConditionalOnBean({OutboxRepository.class, KafkaTemplate.class})
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxEventPublisher outboxEventPublisher(
        OutboxRepository repository, ObjectMapper objectMapper, Clock clock
    ) {
        return new OutboxEventPublisher(repository, objectMapper, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(
        OutboxRepository repository,
        KafkaTemplate<String, byte[]> kafkaTemplate,
        Clock clock,
        @Value("${app.outbox.batch-size:100}") int batchSize,
        @Value("${app.outbox.send-timeout-ms:5000}") long sendTimeoutMs
    ) {
        return new OutboxRelay(repository, kafkaTemplate, clock, batchSize, sendTimeoutMs);
    }
}

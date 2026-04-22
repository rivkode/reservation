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
 * <p>{@link OutboxEventPublisher} 는 {@link OutboxRepository} 빈이 존재하면 등록되고,
 * {@link OutboxRelay} 는 추가로 {@link KafkaTemplate} 빈이 존재할 때만 등록된다.
 * 이렇게 분리해 테스트 환경(실제 Kafka 미기동)에서 publisher 만이라도 Application
 * Service 주입이 가능하게 한다. Relay 가 없으면 DB 적재만 되고 발행은 되지 않아
 * 로컬 단위 테스트의 assertion 에는 영향이 없다.
 *
 * <p>{@code @EnableScheduling} 으로 relay 의 {@code @Scheduled} 를 활성화한다.
 * 소비 서비스가 별도 스케줄링 설정을 가질 경우 충돌 없이 병존한다.
 */
@AutoConfiguration
@ConditionalOnClass(OutboxRepository.class)
@ConditionalOnBean(OutboxRepository.class)
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
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnBean(KafkaTemplate.class)
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

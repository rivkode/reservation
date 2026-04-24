package com.reservation.hotel.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * hotel-service Kafka consumer 설정. common-infrastructure 는 Outbox relay producer 만
 * 제공하므로 consumer 측 factory 는 각 서비스가 자체 정의한다 (reservation-service 와
 * 동일 컨벤션, PR-2.1 참조).
 *
 * <p>payload 를 {@code byte[]} 로 받아 {@code ReservationEventsKafkaConsumer} 에서
 * event-type 헤더 기반 디스패치 후 수동 역직렬화한다. contracts 이벤트 record 에
 * {@code @JsonTypeInfo} 등 다형 역직렬화 메타가 없어도 헤더 스위치만으로 구체 타입을
 * 결정할 수 있다.
 *
 * <p>{@code enable-auto-commit=false} · {@code auto-offset-reset=earliest} 는 at-least-once
 * + consumer 그룹 신규 배포 시 유실 방지를 위해 명시. 오프셋 커밋은 Spring Kafka 의 기본
 * {@code AckMode.BATCH} 로 리스너 메서드가 예외 없이 반환한 후 커밋된다.
 */
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, byte[]> reservationEventsConsumerFactory(
        @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
        @Value("${app.kafka.consumer.reservation-events.group-id:hotel-service.reservation-events}") String groupId) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> reservationEventsListenerContainerFactory(
        ConsumerFactory<String, byte[]> reservationEventsConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(reservationEventsConsumerFactory);
        return factory;
    }
}

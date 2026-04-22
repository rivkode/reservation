package com.reservation.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * 서비스 전반의 Jackson {@code ObjectMapper} 공용 설정.
 *
 * <ul>
 *   <li>{@link JavaTimeModule} 등록 — {@code Instant} · {@code LocalDate} ·
 *       {@code ZonedDateTime} 등 {@code java.time} 타입 직렬화 지원</li>
 *   <li>날짜는 <strong>ISO-8601 문자열</strong> 로 직렬화
 *       ({@code WRITE_DATES_AS_TIMESTAMPS} 비활성) — 사람이 읽기 쉽고 타 시스템
 *       호환성도 높음</li>
 *   <li>{@code null} 필드는 직렬화에서 제외 ({@code JsonInclude.Include.NON_NULL}) —
 *       API 응답 경량화 + 의도치 않은 필드 노출 최소화</li>
 * </ul>
 *
 * <p>Spring Boot 의 기본 {@code JacksonAutoConfiguration} 에 추가 customizer 를
 * 등록하는 방식이므로 소비 서비스가 별도 {@code Jackson2ObjectMapperBuilderCustomizer}
 * 를 선언하면 체인되어 적용된다 (override 가능).
 */
@AutoConfiguration
@ConditionalOnClass(JavaTimeModule.class)
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .modulesToInstall(new JavaTimeModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}

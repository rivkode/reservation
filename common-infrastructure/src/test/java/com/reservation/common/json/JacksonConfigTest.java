package com.reservation.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, JacksonConfig.class));

    @Test
    @DisplayName("Instant 는 ISO-8601 문자열로 직렬화된다 (timestamp 숫자 아님)")
    void instantSerialisesAsIso8601String() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            Instant instant = Instant.parse("2026-04-22T10:00:00Z");

            String json = mapper.writeValueAsString(instant);

            assertThat(json).isEqualTo("\"2026-04-22T10:00:00Z\"");
        });
    }

    @Test
    @DisplayName("null 필드는 직렬화 결과에 포함되지 않는다")
    void nullFieldsAreExcluded() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            Payload p = new Payload("Alice", null);

            String json = mapper.writeValueAsString(p);

            assertThat(json).isEqualTo("{\"name\":\"Alice\"}");
        });
    }

    private record Payload(String name, String email) {
    }
}

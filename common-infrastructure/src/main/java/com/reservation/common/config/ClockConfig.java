package com.reservation.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * 도메인 · 애플리케이션 계층에서 현재 시각을 주입받기 위한 공용 {@link Clock} 빈.
 *
 * <p>{@code @AutoConfiguration} 으로 선언되어 있어 각 서비스의 {@code @SpringBootApplication}
 * 컴포넌트 스캔 범위(예: {@code com.reservation.hotel}) 밖에 있어도 Spring Boot 의
 * auto-configuration 메커니즘을 통해 자동 등록된다. 이 모듈의
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 에 FQCN 이 등록되어 있다.
 *
 * <p>프로덕션 기본값은 시스템 UTC 시각이며, 테스트는 자체 {@code @TestConfiguration}
 * 에서 {@code @Bean Clock fixedClock()} 을 먼저 등록해 override 할 수 있다
 * ({@link ConditionalOnMissingBean}).
 */
@AutoConfiguration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}

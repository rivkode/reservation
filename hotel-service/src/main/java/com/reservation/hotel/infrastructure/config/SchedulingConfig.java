package com.reservation.hotel.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 지원 활성화. AvailabilityRebuildScheduler 의 cron 트리거가 동작하려면
 * 필요. 테스트 프로파일에서는 scheduler 빈이 {@code @ConditionalOnProperty} 로 빠지므로
 * 본 클래스는 항상 로드돼도 무방.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

package com.reservation.reservation.infrastructure.config;

import com.reservation.reservation.domain.model.CancellationPolicy;
import com.reservation.reservation.domain.model.TwentyFourHourCancellationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 정책 객체 ({@link CancellationPolicy}) 의 Spring Bean 등록 지점.
 *
 * <p>{@link TwentyFourHourCancellationPolicy} 자체는 Spring 어노테이션을 지니지 않는 순수
 * 도메인 클래스이므로 본 Configuration 에서 {@code @Bean} 으로 등록한다 — 도메인이
 * 프레임워크에 의존하지 않게 유지하면서 DI 컨테이너로 흘려보내는 표준 패턴.
 *
 * <p>향후 정책이 분화 (예: VIP 면제, 호텔별 정책) 되면 본 Config 에서 분기 등록하거나
 * {@code @ConditionalOnProperty} 로 분리한다.
 */
@Configuration
public class CancellationPolicyConfig {

    @Bean
    public CancellationPolicy cancellationPolicy() {
        return new TwentyFourHourCancellationPolicy();
    }
}

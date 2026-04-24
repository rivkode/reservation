package com.reservation.hotel.infrastructure.scheduler;

import com.reservation.hotel.application.service.AvailabilityRebuildApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FR-H-08 — 가용성 캐시 재구축 트리거. ADR 0004 운영 정책.
 *
 * <ol>
 *   <li><strong>서비스 기동 시 1회</strong> — {@link ApplicationReadyEvent} 로 cold start
 *       대비 초기 채움. Redis 장애 후 재시작으로도 복구 가능.</li>
 *   <li><strong>매일 02:00 KST</strong> — 이벤트 유실 / 드리프트 안전망. cron 은 설정으로
 *       외출해 배포 환경별 조정 가능.</li>
 * </ol>
 *
 * <p>재구축 로직은 {@link AvailabilityRebuildApplicationService} 가 담당하며 본 클래스는
 * 단순 트리거. 예외는 스케줄러가 삼켜 다음 주기까지 기다린다 (Service 는 이미 내부에서
 * 예외를 요약 Result 로 변환).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.availability.rebuild.enabled", havingValue = "true", matchIfMissing = true)
public class AvailabilityRebuildScheduler {

    private final AvailabilityRebuildApplicationService rebuildService;

    /**
     * 서비스 기동 시 1회. application context 가 완전히 올라온 뒤 실행돼 reservation-service
     * gRPC 호출에 문제가 없다. {@link RuntimeException} 전파 시 ApplicationReadyEvent 리스너
     * 기본 동작상 애플리케이션 기동은 계속되지만, 운영 로그에 불필요한 stack trace 가 쌓이지
     * 않도록 명시적으로 감싸 WARN 한 줄만 남긴다 — 다음 daily tick 이 복구.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Triggering rebuild on application ready");
        runSwallowing("application-ready");
    }

    /**
     * 매일 새벽 2시 (Asia/Seoul) — ADR 0004 규약. 기본값은 코드에 두되 운영 긴급 시
     * {@code app.availability.rebuild.cron} · {@code .zone} 을 override 할 수 있도록 외출.
     * Scheduler 스레드에서 예외가 전파되면 다음 tick 이 취소되는 구현체가 있어 방어적으로
     * 감싼다.
     */
    @Scheduled(
        cron = "${app.availability.rebuild.cron:0 0 2 * * *}",
        zone = "${app.availability.rebuild.zone:Asia/Seoul}")
    public void onDailySchedule() {
        log.info("Triggering rebuild on daily schedule");
        runSwallowing("daily-schedule");
    }

    private void runSwallowing(String triggerLabel) {
        try {
            rebuildService.rebuildAll();
        } catch (RuntimeException e) {
            log.warn("Rebuild trigger '{}' failed — next tick will retry: {}",
                triggerLabel, e.getMessage(), e);
        }
    }
}

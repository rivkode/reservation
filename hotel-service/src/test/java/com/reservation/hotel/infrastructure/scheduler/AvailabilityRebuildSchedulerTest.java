package com.reservation.hotel.infrastructure.scheduler;

import com.reservation.hotel.application.dto.RebuildResult;
import com.reservation.hotel.application.service.AvailabilityRebuildApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityRebuildScheduler — 트리거 델리게이션")
class AvailabilityRebuildSchedulerTest {

    @Mock
    AvailabilityRebuildApplicationService service;

    AvailabilityRebuildScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AvailabilityRebuildScheduler(service);
    }

    @Test
    @DisplayName("onApplicationReady → rebuildService.rebuildAll 호출")
    void startup_triggers_rebuild() {
        given(service.rebuildAll()).willReturn(RebuildResult.empty());

        scheduler.onApplicationReady();

        verify(service).rebuildAll();
    }

    @Test
    @DisplayName("onDailySchedule → rebuildService.rebuildAll 호출")
    void daily_schedule_triggers_rebuild() {
        given(service.rebuildAll()).willReturn(RebuildResult.empty());

        scheduler.onDailySchedule();

        verify(service).rebuildAll();
    }

    @Test
    @DisplayName("rebuildAll 이 RuntimeException 던져도 onApplicationReady 는 예외 전파 없이 swallow")
    void swallows_exception_on_application_ready() {
        given(service.rebuildAll()).willThrow(new RuntimeException("redis down on startup"));

        assertThatCode(() -> scheduler.onApplicationReady()).doesNotThrowAnyException();
        verify(service).rebuildAll();
    }

    @Test
    @DisplayName("rebuildAll 이 RuntimeException 던져도 onDailySchedule 은 예외 전파 없이 swallow")
    void swallows_exception_on_daily_schedule() {
        given(service.rebuildAll()).willThrow(new RuntimeException("grpc down"));

        assertThatCode(() -> scheduler.onDailySchedule()).doesNotThrowAnyException();
        verify(service).rebuildAll();
    }
}

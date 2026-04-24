package com.reservation.hotel.application.service;

import com.reservation.hotel.application.dto.InventoryRebuildEntry;
import com.reservation.hotel.application.dto.RebuildResult;
import com.reservation.hotel.application.port.InventorySnapshotSource;
import com.reservation.hotel.application.port.RoomAvailabilityRebuilder;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.domain.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityRebuildApplicationService")
class AvailabilityRebuildApplicationServiceTest {

    private static final Instant FIXED = Instant.parse("2026-06-01T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    private static final HotelId HOTEL_A =
        HotelId.of(UUID.fromString("01933333-aaaa-7aaa-9aaa-111122223333"));
    private static final HotelId HOTEL_B =
        HotelId.of(UUID.fromString("01933333-bbbb-7aaa-9aaa-111122223333"));
    private static final RoomTypeId ROOM_TYPE =
        RoomTypeId.of(UUID.fromString("01933333-2222-7aaa-9aaa-111122223333"));

    @Mock
    HotelRepository hotelRepository;
    @Mock
    InventorySnapshotSource snapshotSource;
    @Mock
    RoomAvailabilityRebuilder rebuilder;

    AvailabilityRebuildApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityRebuildApplicationService(
            hotelRepository, snapshotSource, rebuilder, CLOCK, 90);
    }

    @Test
    @DisplayName("모든 호텔 순회 — gRPC 결과를 Rebuilder.upsertAll 로 전달 + 요약 반환")
    void processes_all_hotels_and_aggregates_entries() {
        given(hotelRepository.findAllIds()).willReturn(List.of(HOTEL_A, HOTEL_B));
        InventoryRebuildEntry entryA = new InventoryRebuildEntry(
            HOTEL_A, ROOM_TYPE, LocalDate.of(2026, 6, 1), 3, 10, FIXED);
        InventoryRebuildEntry entryB = new InventoryRebuildEntry(
            HOTEL_B, ROOM_TYPE, LocalDate.of(2026, 6, 1), 5, 10, FIXED);
        // proto 가 from/to 모두 inclusive 이므로 90일 범위 = [2026-06-01, 2026-08-29]
        given(snapshotSource.findByHotelInRange(HOTEL_A,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 29)))
            .willReturn(List.of(entryA));
        given(snapshotSource.findByHotelInRange(HOTEL_B,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 29)))
            .willReturn(List.of(entryB));

        RebuildResult result = service.rebuildAll();

        assertThat(result.hotelsProcessed()).isEqualTo(2);
        assertThat(result.hotelsFailed()).isEqualTo(0);
        assertThat(result.entriesWritten()).isEqualTo(2);
        verify(rebuilder).upsertAll(List.of(entryA));
        verify(rebuilder).upsertAll(List.of(entryB));
    }

    @Test
    @DisplayName("빈 응답 호텔은 Rebuilder 호출 생략하되 processed 로 집계")
    void skips_rebuilder_on_empty_response() {
        given(hotelRepository.findAllIds()).willReturn(List.of(HOTEL_A));
        given(snapshotSource.findByHotelInRange(any(), any(), any())).willReturn(List.of());

        RebuildResult result = service.rebuildAll();

        assertThat(result.hotelsProcessed()).isEqualTo(1);
        assertThat(result.entriesWritten()).isEqualTo(0);
        verify(rebuilder, never()).upsertAll(any());
    }

    @Test
    @DisplayName("한 호텔이 InventoryStreamUnavailableException — 나머지 호텔 계속 진행, failed 집계")
    void continues_on_per_hotel_failure() {
        given(hotelRepository.findAllIds()).willReturn(List.of(HOTEL_A, HOTEL_B));
        given(snapshotSource.findByHotelInRange(eq(HOTEL_A), any(), any()))
            .willThrow(new InventorySnapshotSource.InventoryStreamUnavailableException(
                "reservation-service unavailable", new RuntimeException()));
        InventoryRebuildEntry entryB = new InventoryRebuildEntry(
            HOTEL_B, ROOM_TYPE, LocalDate.of(2026, 6, 1), 5, 10, FIXED);
        given(snapshotSource.findByHotelInRange(eq(HOTEL_B), any(), any()))
            .willReturn(List.of(entryB));

        RebuildResult result = service.rebuildAll();

        assertThat(result.hotelsProcessed()).isEqualTo(1);
        assertThat(result.hotelsFailed()).isEqualTo(1);
        assertThat(result.entriesWritten()).isEqualTo(1);
        verify(rebuilder).upsertAll(List.of(entryB));
    }

    @Test
    @DisplayName("호텔이 0개 — 포트 호출 없이 빈 요약 반환")
    void returns_empty_when_no_hotels() {
        given(hotelRepository.findAllIds()).willReturn(List.of());

        RebuildResult result = service.rebuildAll();

        assertThat(result.hotelsProcessed()).isEqualTo(0);
        assertThat(result.entriesWritten()).isEqualTo(0);
        verify(rebuilder, never()).upsertAll(any());
    }

    @Test
    @DisplayName("gRPC 요청 날짜 범위: [today, today + horizonDays - 1] inclusive")
    void passes_correct_date_range() {
        given(hotelRepository.findAllIds()).willReturn(List.of(HOTEL_A));
        given(snapshotSource.findByHotelInRange(any(), any(), any())).willReturn(List.of());

        service.rebuildAll();

        ArgumentCaptor<LocalDate> fromCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCap = ArgumentCaptor.forClass(LocalDate.class);
        verify(snapshotSource).findByHotelInRange(eq(HOTEL_A), fromCap.capture(), toCap.capture());
        assertThat(fromCap.getValue()).isEqualTo(LocalDate.of(2026, 6, 1));
        // 90일 horizon 이 inclusive 이므로 끝 날짜는 today + 89.
        assertThat(toCap.getValue()).isEqualTo(LocalDate.of(2026, 8, 29));
    }

    @Test
    @DisplayName("이미 실행 중인 상태면 두 번째 트리거는 empty 즉시 반환 + 포트 호출 없음")
    void skips_when_already_running() throws Exception {
        java.util.concurrent.CountDownLatch firstStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        given(hotelRepository.findAllIds()).willReturn(List.of(HOTEL_A));
        given(snapshotSource.findByHotelInRange(any(), any(), any()))
            .willAnswer(inv -> {
                firstStarted.countDown();
                release.await();
                return List.of();
            });

        Thread first = new Thread(() -> service.rebuildAll());
        first.start();
        firstStarted.await();

        RebuildResult second = service.rebuildAll();

        assertThat(second).isEqualTo(RebuildResult.empty());
        release.countDown();
        first.join();
    }

    @Test
    @DisplayName("예외 발생 후에도 running flag 가 finally 로 해제되어 다음 호출은 정상 실행")
    void running_flag_released_after_exception() {
        given(hotelRepository.findAllIds())
            .willThrow(new RuntimeException("boom"))
            .willReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(service::rebuildAll)
            .isInstanceOf(RuntimeException.class);

        RebuildResult second = service.rebuildAll();
        assertThat(second).isEqualTo(new RebuildResult(0, 0, 0));
    }

    @Test
    @DisplayName("rebuilder 가 DataAccessException — failed 집계 + 루프 계속")
    void continues_on_rebuilder_runtime_exception() {
        InventoryRebuildEntry entryA = new InventoryRebuildEntry(
            HOTEL_A, ROOM_TYPE, LocalDate.of(2026, 6, 1), 3, 10, FIXED);
        InventoryRebuildEntry entryB = new InventoryRebuildEntry(
            HOTEL_B, ROOM_TYPE, LocalDate.of(2026, 6, 1), 5, 10, FIXED);
        given(hotelRepository.findAllIds()).willReturn(List.of(HOTEL_A, HOTEL_B));
        given(snapshotSource.findByHotelInRange(eq(HOTEL_A), any(), any()))
            .willReturn(List.of(entryA));
        given(snapshotSource.findByHotelInRange(eq(HOTEL_B), any(), any()))
            .willReturn(List.of(entryB));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("redis down"))
            .when(rebuilder).upsertAll(List.of(entryA));

        RebuildResult result = service.rebuildAll();

        assertThat(result.hotelsProcessed()).isEqualTo(1);
        assertThat(result.hotelsFailed()).isEqualTo(1);
        assertThat(result.entriesWritten()).isEqualTo(1);
    }

    @Test
    @DisplayName("horizonDays = -1 도 생성자 IllegalArgumentException")
    void rejects_negative_horizon() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AvailabilityRebuildApplicationService(
                hotelRepository, snapshotSource, rebuilder, CLOCK, -1));
    }

    @Test
    @DisplayName("horizonDays ≤ 0 → 생성자 IllegalArgumentException")
    void rejects_invalid_horizon() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AvailabilityRebuildApplicationService(
                hotelRepository, snapshotSource, rebuilder, CLOCK, 0));
    }
}

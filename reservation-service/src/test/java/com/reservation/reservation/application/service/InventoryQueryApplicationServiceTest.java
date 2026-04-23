package com.reservation.reservation.application.service;

import com.reservation.reservation.application.dto.InventorySnapshotResult;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryCount;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application 단위 테스트 — Repository 는 Mock 으로 대체하고 범위 경계 검증과 DTO
 * 변환만 본다. 실제 JPA 쿼리는 {@code RoomTypeInventoryPersistenceTest} 가 별도로 검증.
 */
@DisplayName("InventoryQueryApplicationService 단위 테스트")
class InventoryQueryApplicationServiceTest {

    private static final HotelId HOTEL_ID = HotelId.of("01933333-1111-7aaa-9aaa-111122223333");
    private static final RoomTypeId ROOM_TYPE_ID = RoomTypeId.of("01933333-aaaa-7aaa-9aaa-111122223333");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-23T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 3);

    private static final int MAX_RANGE_DAYS = 400;

    private RoomTypeInventoryRepository repository;
    private InventoryQueryApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(RoomTypeInventoryRepository.class);
        service = new InventoryQueryApplicationService(repository, MAX_RANGE_DAYS);
    }

    @Test
    @DisplayName("Repository 결과를 DTO 로 변환해 순서대로 반환")
    void 정상_DTO_변환() {
        RoomTypeInventory inv1 = inventoryOf(ROOM_TYPE_ID, FROM, 5, 3);
        RoomTypeInventory inv2 = inventoryOf(ROOM_TYPE_ID, TO, 5, 5);
        when(repository.findRangeByHotel(HOTEL_ID, FROM, TO)).thenReturn(List.of(inv1, inv2));

        List<InventorySnapshotResult> results = service.findByHotelInRange(HOTEL_ID, FROM, TO);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).hotelId()).isEqualTo(HOTEL_ID.asString());
        assertThat(results.get(0).roomTypeId()).isEqualTo(ROOM_TYPE_ID.asString());
        assertThat(results.get(0).stayDate()).isEqualTo(FROM);
        assertThat(results.get(0).totalInventory()).isEqualTo(5);
        assertThat(results.get(0).available()).isEqualTo(3);
        assertThat(results.get(1).stayDate()).isEqualTo(TO);
        assertThat(results.get(1).available()).isEqualTo(5);
    }

    @Test
    @DisplayName("결과 없으면 빈 리스트 반환 — 에러 아님 (미등록 호텔 흡수)")
    void 빈_결과() {
        when(repository.findRangeByHotel(HOTEL_ID, FROM, TO)).thenReturn(List.of());

        List<InventorySnapshotResult> results = service.findByHotelInRange(HOTEL_ID, FROM, TO);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("from > to 이면 IllegalArgumentException — Repository 호출 없음")
    void 역전된_날짜는_거부() {
        assertThatThrownBy(() -> service.findByHotelInRange(HOTEL_ID, TO, FROM))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("from must not be after to");
        verify(repository, never()).findRangeByHotel(any(), any(), any());
    }

    @Test
    @DisplayName("from == to 는 허용 (당일만 조회)")
    void 동일날짜_허용() {
        when(repository.findRangeByHotel(HOTEL_ID, FROM, FROM)).thenReturn(List.of());

        List<InventorySnapshotResult> results = service.findByHotelInRange(HOTEL_ID, FROM, FROM);

        assertThat(results).isEmpty();
        verify(repository).findRangeByHotel(HOTEL_ID, FROM, FROM);
    }

    @Test
    @DisplayName("경계: 정확히 MAX_RANGE_DAYS 일 범위는 허용")
    void 경계값_MAX_포함() {
        LocalDate exactMax = FROM.plusDays(MAX_RANGE_DAYS - 1);
        when(repository.findRangeByHotel(HOTEL_ID, FROM, exactMax)).thenReturn(List.of());

        assertThat(service.findByHotelInRange(HOTEL_ID, FROM, exactMax)).isEmpty();
        verify(repository).findRangeByHotel(HOTEL_ID, FROM, exactMax);
    }

    @Test
    @DisplayName("경계: MAX_RANGE_DAYS + 1 일은 IllegalArgumentException")
    void 경계값_MAX_초과() {
        LocalDate overMax = FROM.plusDays(MAX_RANGE_DAYS);

        assertThatThrownBy(() -> service.findByHotelInRange(HOTEL_ID, FROM, overMax))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("range too large");
        verify(repository, never()).findRangeByHotel(any(), any(), any());
    }

    @Test
    @DisplayName("null 인자는 NPE — Repository 호출 없음")
    void null_인자_거부() {
        assertThatThrownBy(() -> service.findByHotelInRange(null, FROM, TO))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("hotelId");
        assertThatThrownBy(() -> service.findByHotelInRange(HOTEL_ID, null, TO))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("from");
        assertThatThrownBy(() -> service.findByHotelInRange(HOTEL_ID, FROM, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("to");
        verify(repository, never()).findRangeByHotel(any(), any(), any());
    }

    @Test
    @DisplayName("maxRangeDays 설정은 생성자에서 검증 — 0 이하 금지")
    void maxRangeDays_검증() {
        assertThatThrownBy(() -> new InventoryQueryApplicationService(repository, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("max-range-days");
        assertThatThrownBy(() -> new InventoryQueryApplicationService(repository, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static RoomTypeInventory inventoryOf(RoomTypeId roomTypeId, LocalDate date,
                                                  int total, int available) {
        return RoomTypeInventory.restore(
            new InventoryKey(HOTEL_ID, roomTypeId, date),
            InventoryCount.of(total),
            InventoryCount.of(available),
            0L,
            Instant.now(CLOCK),
            Instant.now(CLOCK)
        );
    }
}

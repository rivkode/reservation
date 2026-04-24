package com.reservation.hotel.application.service;

import com.reservation.hotel.application.dto.AvailabilityQuery;
import com.reservation.hotel.application.dto.AvailabilityResult;
import com.reservation.hotel.application.dto.DailyAvailability;
import com.reservation.hotel.application.dto.RoomAvailabilitySnapshot;
import com.reservation.hotel.application.port.RoomAvailabilityQuery;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityQueryApplicationService")
class AvailabilityQueryApplicationServiceTest {

    private static final String HOTEL_ID = "01933333-1111-7aaa-9aaa-111122223333";
    private static final String ROOM_TYPE_ID = "01933333-2222-7aaa-9aaa-111122223333";

    @Mock
    private RoomAvailabilityQuery port;

    private AvailabilityQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityQueryApplicationService(port, 90);
    }

    @Test
    @DisplayName("정상 조회 → 날짜별 결과 + staleUntil = min(updatedAt)+30s")
    void returns_daily_availability_and_stale_until() {
        // given — 포트가 2박 스냅샷 반환, updatedAt 두 개 중 가장 오래된 것 기준
        AvailabilityQuery query = new AvailabilityQuery(HOTEL_ID, ROOM_TYPE_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));
        Instant olderUpdate = Instant.parse("2026-06-01T10:00:00Z");
        Instant newerUpdate = Instant.parse("2026-06-01T10:00:15Z");
        given(port.readRange(HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID),
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3)))
            .willReturn(List.of(
                new RoomAvailabilitySnapshot(LocalDate.of(2026, 6, 1), 3, 10, olderUpdate),
                new RoomAvailabilitySnapshot(LocalDate.of(2026, 6, 2), 3, 10, newerUpdate)));

        // when
        AvailabilityResult result = service.query(query);

        // then
        assertThat(result.hotelId()).isEqualTo(HOTEL_ID);
        assertThat(result.roomTypeId()).isEqualTo(ROOM_TYPE_ID);
        assertThat(result.availability()).containsExactly(
            new DailyAvailability(LocalDate.of(2026, 6, 1), 3, 10),
            new DailyAvailability(LocalDate.of(2026, 6, 2), 3, 10));
        assertThat(result.staleUntil()).isEqualTo(olderUpdate.plusSeconds(30));
    }

    @Test
    @DisplayName("포트가 빈 리스트 반환 → availability 비어있고 staleUntil=null")
    void empty_result_yields_null_stale_until() {
        AvailabilityQuery query = new AvailabilityQuery(HOTEL_ID, ROOM_TYPE_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));
        given(port.readRange(any(), any(), any(), any())).willReturn(List.of());

        AvailabilityResult result = service.query(query);

        assertThat(result.availability()).isEmpty();
        assertThat(result.staleUntil()).isNull();
    }

    @Test
    @DisplayName("checkIn == checkOut → IllegalArgumentException, 포트 호출 없음")
    void rejects_empty_range() {
        AvailabilityQuery query = new AvailabilityQuery(HOTEL_ID, ROOM_TYPE_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.query(query))
            .withMessageContaining("checkOut must be after checkIn");

        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("checkOut < checkIn → IllegalArgumentException")
    void rejects_inverted_range() {
        AvailabilityQuery query = new AvailabilityQuery(HOTEL_ID, ROOM_TYPE_ID,
            LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 1));

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.query(query))
            .withMessageContaining("checkOut must be after checkIn");

        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("범위가 최대 허용 일수(90) 초과 → IllegalArgumentException")
    void rejects_range_exceeding_max_days() {
        AvailabilityQuery query = new AvailabilityQuery(HOTEL_ID, ROOM_TYPE_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1)); // 92 days

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.query(query))
            .withMessageContaining("exceeds max");

        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("범위가 정확히 90일 경계 → 정상 통과")
    void accepts_range_at_max_boundary() {
        AvailabilityQuery query = new AvailabilityQuery(HOTEL_ID, ROOM_TYPE_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 30)); // 90 days
        given(port.readRange(any(), any(), any(), any())).willReturn(List.of());

        AvailabilityResult result = service.query(query);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("null query → NPE")
    void null_query_throws_npe() {
        assertThatNullPointerException().isThrownBy(() -> service.query(null));
    }

    @Test
    @DisplayName("maxRangeDays 가 0 이하면 생성 시점에 IllegalArgumentException")
    void rejects_invalid_max_range_configuration() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AvailabilityQueryApplicationService(port, 0));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new AvailabilityQueryApplicationService(port, -1));
    }
}

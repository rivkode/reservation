package com.reservation.hotel.application.service;

import com.reservation.contracts.event.reservation.ReservationCancelledEvent;
import com.reservation.contracts.event.reservation.ReservationCreatedEvent;
import com.reservation.hotel.application.idempotency.ProcessedEventStore;
import com.reservation.hotel.application.port.RoomAvailabilityCache;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationEventApplicationService — reservation-events → Redis 캐시 증감")
class ReservationEventApplicationServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-01T10:00:00Z");
    private static final String HOTEL_ID = "01933333-1111-7aaa-9aaa-111122223333";
    private static final String ROOM_TYPE_ID = "01933333-2222-7aaa-9aaa-111122223333";

    @Mock
    private RoomAvailabilityCache cache;

    @Mock
    private ProcessedEventStore processedEventStore;

    private ReservationEventApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        service = new ReservationEventApplicationService(cache, processedEventStore, clock);
    }

    @Nested
    @DisplayName("onReservationCreated")
    class OnReservationCreated {

        @Test
        @DisplayName("숙박 기간의 각 날짜에 대해 delta=-1 로 adjustAvailable 호출 후 markProcessed")
        void applies_negative_delta_for_each_stay_day() {
            // given
            LocalDate checkIn = LocalDate.of(2026, 7, 10);
            LocalDate checkOut = LocalDate.of(2026, 7, 13); // 3박
            ReservationCreatedEvent event = createdEvent(checkIn, checkOut);
            given(cache.adjustAvailable(any(), any(), any(), anyInt(), any())).willReturn(true);

            // when
            service.onReservationCreated(event);

            // then
            ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(cache, org.mockito.Mockito.times(3))
                .adjustAvailable(eq(HotelId.of(HOTEL_ID)), eq(RoomTypeId.of(ROOM_TYPE_ID)),
                    dateCaptor.capture(), eq(-1), eq(FIXED_INSTANT));
            assertThat(dateCaptor.getAllValues()).containsExactly(
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2026, 7, 12)); // 체크아웃 당일 7/13 제외

            verify(processedEventStore)
                .markProcessed(event.eventId(), "ReservationCreatedEvent", FIXED_INSTANT);
        }

        @Test
        @DisplayName("이미 처리된 이벤트는 cache 및 markProcessed 호출 없이 early return")
        void skips_when_already_processed() {
            // given
            ReservationCreatedEvent event = createdEvent(LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 13));
            given(processedEventStore.isAlreadyProcessed(event.eventId())).willReturn(true);

            // when
            service.onReservationCreated(event);

            // then
            verify(processedEventStore).isAlreadyProcessed(event.eventId());
            verifyNoInteractions(cache);
            verify(processedEventStore, never()).markProcessed(any(), any(), any());
        }

        @Test
        @DisplayName("key 부재로 일부 날짜가 skip 되어도 markProcessed 는 수행")
        void still_marks_processed_when_some_days_missing() {
            // given
            LocalDate checkIn = LocalDate.of(2026, 7, 10);
            LocalDate checkOut = LocalDate.of(2026, 7, 12); // 2박
            ReservationCreatedEvent event = createdEvent(checkIn, checkOut);
            given(cache.adjustAvailable(any(), any(), eq(LocalDate.of(2026, 7, 10)),
                anyInt(), any())).willReturn(true);
            given(cache.adjustAvailable(any(), any(), eq(LocalDate.of(2026, 7, 11)),
                anyInt(), any())).willReturn(false);

            // when
            service.onReservationCreated(event);

            // then — 두 날짜 모두 호출 시도되었고, markProcessed 는 여전히 수행
            verify(cache, org.mockito.Mockito.times(2))
                .adjustAvailable(any(), any(), any(), anyInt(), any());
            verify(processedEventStore)
                .markProcessed(event.eventId(), "ReservationCreatedEvent", FIXED_INSTANT);
        }

        @Test
        @DisplayName("key 부재로 모든 날짜가 skip 되어도 markProcessed 는 수행")
        void still_marks_processed_when_all_days_missing() {
            // given
            ReservationCreatedEvent event = createdEvent(LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 13));
            given(cache.adjustAvailable(any(), any(), any(), anyInt(), any())).willReturn(false);

            // when
            service.onReservationCreated(event);

            // then
            verify(cache, org.mockito.Mockito.times(3))
                .adjustAvailable(any(), any(), any(), anyInt(), any());
            verify(processedEventStore)
                .markProcessed(event.eventId(), "ReservationCreatedEvent", FIXED_INSTANT);
        }

        @Test
        @DisplayName("Redis 장애가 특정 날짜에 발생 → 해당 날짜만 skip 하고 나머지 루프 이어감 + markProcessed")
        void isolates_partial_redis_failure() {
            // given — 2/3 은 정상, 1일만 Redis 장애 (DataAccessException)
            LocalDate checkIn = LocalDate.of(2026, 7, 10);
            LocalDate checkOut = LocalDate.of(2026, 7, 13);
            ReservationCreatedEvent event = createdEvent(checkIn, checkOut);
            given(cache.adjustAvailable(any(), any(), eq(LocalDate.of(2026, 7, 10)),
                anyInt(), any())).willReturn(true);
            given(cache.adjustAvailable(any(), any(), eq(LocalDate.of(2026, 7, 11)),
                anyInt(), any())).willThrow(new QueryTimeoutException("redis timeout"));
            given(cache.adjustAvailable(any(), any(), eq(LocalDate.of(2026, 7, 12)),
                anyInt(), any())).willReturn(true);

            // when
            service.onReservationCreated(event);

            // then — 세 날짜 모두 호출 시도되고 markProcessed 수행 (이중 증감 방지)
            verify(cache, org.mockito.Mockito.times(3))
                .adjustAvailable(any(), any(), any(), anyInt(), any());
            verify(processedEventStore)
                .markProcessed(event.eventId(), "ReservationCreatedEvent", FIXED_INSTANT);
        }
    }

    @Nested
    @DisplayName("onReservationCancelled")
    class OnReservationCancelled {

        @Test
        @DisplayName("숙박 기간의 각 날짜에 대해 delta=+1 로 adjustAvailable 호출 후 markProcessed")
        void applies_positive_delta_for_each_stay_day() {
            // given
            LocalDate checkIn = LocalDate.of(2026, 8, 1);
            LocalDate checkOut = LocalDate.of(2026, 8, 3); // 2박
            ReservationCancelledEvent event = cancelledEvent(checkIn, checkOut);
            given(cache.adjustAvailable(any(), any(), any(), anyInt(), any())).willReturn(true);

            // when
            service.onReservationCancelled(event);

            // then
            verify(cache)
                .adjustAvailable(HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID),
                    LocalDate.of(2026, 8, 1), +1, FIXED_INSTANT);
            verify(cache)
                .adjustAvailable(HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID),
                    LocalDate.of(2026, 8, 2), +1, FIXED_INSTANT);
            verify(processedEventStore)
                .markProcessed(event.eventId(), "ReservationCancelledEvent", FIXED_INSTANT);
        }

        @Test
        @DisplayName("이미 처리된 이벤트는 cache 및 markProcessed 호출 없이 early return")
        void skips_when_already_processed() {
            // given
            ReservationCancelledEvent event = cancelledEvent(LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3));
            given(processedEventStore.isAlreadyProcessed(event.eventId())).willReturn(true);

            // when
            service.onReservationCancelled(event);

            // then
            verify(processedEventStore).isAlreadyProcessed(event.eventId());
            verifyNoInteractions(cache);
            verify(processedEventStore, never()).markProcessed(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("방어적 입력 검증")
    class DefensiveInput {

        @Test
        @DisplayName("null 이벤트는 NPE")
        void null_created_event_throws_npe() {
            assertThatNullPointerException()
                .isThrownBy(() -> service.onReservationCreated(null));
        }

        @Test
        @DisplayName("null 이벤트는 NPE (cancelled)")
        void null_cancelled_event_throws_npe() {
            assertThatNullPointerException()
                .isThrownBy(() -> service.onReservationCancelled(null));
        }

        @Test
        @DisplayName("checkIn==checkOut 빈 숙박 구간 → cache 호출 없이 markProcessed 만 수행")
        void empty_stay_range_still_marks_processed() {
            // given
            LocalDate date = LocalDate.of(2026, 9, 1);
            ReservationCreatedEvent event = createdEvent(date, date);

            // when
            service.onReservationCreated(event);

            // then
            verifyNoInteractions(cache);
            verify(processedEventStore)
                .markProcessed(event.eventId(), "ReservationCreatedEvent", FIXED_INSTANT);
        }

        @Test
        @DisplayName("checkOut<checkIn 역전된 구간 → cache 호출 없이 markProcessed 만 수행")
        void inverted_stay_range_still_marks_processed() {
            // given
            ReservationCreatedEvent event = createdEvent(LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 1));

            // when
            service.onReservationCreated(event);

            // then
            verifyNoInteractions(cache);
            verify(processedEventStore)
                .markProcessed(event.eventId(), "ReservationCreatedEvent", FIXED_INSTANT);
        }
    }

    private ReservationCreatedEvent createdEvent(LocalDate checkIn, LocalDate checkOut) {
        return new ReservationCreatedEvent(
            UUID.randomUUID(),
            FIXED_INSTANT,
            "R-20260601-001",
            HOTEL_ID,
            ROOM_TYPE_ID,
            "01933333-3333-7aaa-9aaa-111122223333",
            checkIn,
            checkOut,
            2,
            300_000L,
            "KRW");
    }

    private ReservationCancelledEvent cancelledEvent(LocalDate checkIn, LocalDate checkOut) {
        return new ReservationCancelledEvent(
            UUID.randomUUID(),
            FIXED_INSTANT,
            "R-20260601-001",
            HOTEL_ID,
            ROOM_TYPE_ID,
            checkIn,
            checkOut);
    }
}

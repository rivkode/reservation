package com.reservation.hotel.presentation.controller;

import com.reservation.hotel.application.dto.AvailabilityQuery;
import com.reservation.hotel.application.dto.AvailabilityResult;
import com.reservation.hotel.application.dto.DailyAvailability;
import com.reservation.hotel.application.service.AvailabilityQueryApplicationService;
import com.reservation.hotel.presentation.exception.HotelExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AvailabilityController.class)
@Import(HotelExceptionHandler.class)
class AvailabilityControllerTest {

    private static final String HOTEL_ID = "01933333-1111-7aaa-9aaa-111122223333";
    private static final String ROOM_TYPE_ID = "01933333-2222-7aaa-9aaa-111122223333";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AvailabilityQueryApplicationService service;
    @MockBean
    Clock clock;

    @Test
    @DisplayName("GET /api/v1/availability: 200 + CommonResponse · data 에 availability 배열과 staleUntil")
    void returns_200_with_availability() throws Exception {
        given(service.query(any())).willReturn(new AvailabilityResult(
            HOTEL_ID, ROOM_TYPE_ID,
            List.of(
                new DailyAvailability(LocalDate.of(2026, 6, 1), 3, 10),
                new DailyAvailability(LocalDate.of(2026, 6, 2), 3, 10)),
            Instant.parse("2026-06-01T10:00:30Z")));

        mockMvc.perform(get("/api/v1/availability")
                .param("hotelId", HOTEL_ID)
                .param("roomTypeId", ROOM_TYPE_ID)
                .param("checkIn", "2026-06-01")
                .param("checkOut", "2026-06-03"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hotelId").value(HOTEL_ID))
            .andExpect(jsonPath("$.data.roomTypeId").value(ROOM_TYPE_ID))
            .andExpect(jsonPath("$.data.availability[0].date").value("2026-06-01"))
            .andExpect(jsonPath("$.data.availability[0].available").value(3))
            .andExpect(jsonPath("$.data.availability[0].total").value(10))
            .andExpect(jsonPath("$.data.availability[1].date").value("2026-06-02"))
            .andExpect(jsonPath("$.data.staleUntil").value("2026-06-01T10:00:30Z"));
    }

    @Test
    @DisplayName("service 가 IllegalArgumentException → 400 VALIDATION_FAILED")
    void returns_400_on_invalid_range() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-01T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        given(service.query(any()))
            .willThrow(new IllegalArgumentException("checkOut must be after checkIn"));

        mockMvc.perform(get("/api/v1/availability")
                .param("hotelId", HOTEL_ID)
                .param("roomTypeId", ROOM_TYPE_ID)
                .param("checkIn", "2026-06-03")
                .param("checkOut", "2026-06-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest(name = "{0} → 503 AVAILABILITY_CACHE_UNAVAILABLE")
    @MethodSource("redisFailureExceptions")
    @DisplayName("Redis 계열 예외는 모두 503 으로 매핑")
    void returns_503_on_redis_failure(String label, RuntimeException cause) throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-01T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        given(service.query(any())).willThrow(cause);

        mockMvc.perform(get("/api/v1/availability")
                .param("hotelId", HOTEL_ID)
                .param("roomTypeId", ROOM_TYPE_ID)
                .param("checkIn", "2026-06-01")
                .param("checkOut", "2026-06-03"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("AVAILABILITY_CACHE_UNAVAILABLE"));
    }

    static Stream<Arguments> redisFailureExceptions() {
        return Stream.of(
            Arguments.of("RedisConnectionFailureException",
                new RedisConnectionFailureException("cannot connect")),
            Arguments.of("RedisSystemException",
                new RedisSystemException("lua error", new RuntimeException("inner"))));
    }

    @Test
    @DisplayName("필수 파라미터(hotelId) 누락 → 400 VALIDATION_FAILED")
    void returns_400_on_missing_required_param() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-01T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        mockMvc.perform(get("/api/v1/availability")
                .param("roomTypeId", ROOM_TYPE_ID)
                .param("checkIn", "2026-06-01")
                .param("checkOut", "2026-06-03"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("날짜 파라미터 포맷 오류 → 400 VALIDATION_FAILED (500 아님)")
    void returns_400_on_date_format_error() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-01T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        mockMvc.perform(get("/api/v1/availability")
                .param("hotelId", HOTEL_ID)
                .param("roomTypeId", ROOM_TYPE_ID)
                .param("checkIn", "not-a-date")
                .param("checkOut", "2026-06-03"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("빈 availability → staleUntil 필드가 JSON 응답에서 누락 (NON_NULL 정책)")
    void omits_stale_until_on_empty_availability() throws Exception {
        given(service.query(new AvailabilityQuery(
            HOTEL_ID, ROOM_TYPE_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3))))
            .willReturn(new AvailabilityResult(HOTEL_ID, ROOM_TYPE_ID, List.of(), null));

        mockMvc.perform(get("/api/v1/availability")
                .param("hotelId", HOTEL_ID)
                .param("roomTypeId", ROOM_TYPE_ID)
                .param("checkIn", "2026-06-01")
                .param("checkOut", "2026-06-03"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.availability").isArray())
            .andExpect(jsonPath("$.data.availability.length()").value(0))
            .andExpect(jsonPath("$.data.staleUntil").doesNotExist());
    }
}

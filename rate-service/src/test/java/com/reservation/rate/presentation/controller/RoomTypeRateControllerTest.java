package com.reservation.rate.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.rate.application.dto.RoomTypeRateResult;
import com.reservation.rate.application.service.RoomTypeRateApplicationService;
import com.reservation.rate.domain.exception.DuplicateRateException;
import com.reservation.rate.domain.exception.RateNotFoundException;
import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.RateId;
import com.reservation.rate.domain.model.RoomTypeId;
import com.reservation.rate.presentation.dto.ChangeRoomTypeRateRequest;
import com.reservation.rate.presentation.dto.RegisterRoomTypeRateRequest;
import com.reservation.rate.presentation.exception.RateExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomTypeRateController.class)
@Import(RateExceptionHandler.class)
class RoomTypeRateControllerTest {

    private static final UUID HOTEL_UUID = UUID.fromString("01970000-0000-7000-8000-000000000001");
    private static final UUID ROOM_TYPE_UUID = UUID.fromString("01970000-0000-7000-8000-000000000002");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    RoomTypeRateApplicationService service;
    @MockBean
    Clock clock;

    @Test
    @DisplayName("POST /api/v1/room-type-rates: 201 + CommonResponse 래퍼")
    void registerReturns201() throws Exception {
        RoomTypeRateResult result = new RoomTypeRateResult(
            "rate-1", HOTEL_UUID.toString(), ROOM_TYPE_UUID.toString(),
            LocalDate.of(2026, 6, 1), 150_000L, "KRW");
        when(service.register(any())).thenReturn(result);

        var request = new RegisterRoomTypeRateRequest(
            HOTEL_UUID.toString(), ROOM_TYPE_UUID.toString(),
            LocalDate.of(2026, 6, 1), 150_000L, "KRW");

        mockMvc.perform(post("/api/v1/room-type-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value("rate-1"))
            .andExpect(jsonPath("$.data.amount").value(150_000))
            .andExpect(jsonPath("$.data.currency").value("KRW"))
            .andExpect(jsonPath("$.data.date").value("2026-06-01"));
    }

    @Test
    @DisplayName("POST /api/v1/room-type-rates: 자연키 중복이면 409 DUPLICATE_RATE")
    void registerReturns409OnDuplicate() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any())).thenThrow(new DuplicateRateException(
            HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_UUID), LocalDate.of(2026, 6, 1)));

        var request = new RegisterRoomTypeRateRequest(
            HOTEL_UUID.toString(), ROOM_TYPE_UUID.toString(),
            LocalDate.of(2026, 6, 1), 150_000L, "KRW");

        mockMvc.perform(post("/api/v1/room-type-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_RATE"));
    }

    @Test
    @DisplayName("POST /api/v1/room-type-rates: 음수 금액 · 잘못된 통화는 400 VALIDATION_FAILED")
    void registerReturns400OnIllegalArgument() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any())).thenThrow(new IllegalArgumentException("amount must be >= 0"));

        var request = new RegisterRoomTypeRateRequest(
            HOTEL_UUID.toString(), ROOM_TYPE_UUID.toString(),
            LocalDate.of(2026, 6, 1), -1L, "KRW");

        mockMvc.perform(post("/api/v1/room-type-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/room-type-rates/{rateId}: 200 + 변경된 금액 body")
    void changeReturns200() throws Exception {
        RoomTypeRateResult result = new RoomTypeRateResult(
            "rate-1", HOTEL_UUID.toString(), ROOM_TYPE_UUID.toString(),
            LocalDate.of(2026, 6, 1), 180_000L, "KRW");
        when(service.change(any())).thenReturn(result);

        var request = new ChangeRoomTypeRateRequest(180_000L, "KRW");

        mockMvc.perform(patch("/api/v1/room-type-rates/rate-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.amount").value(180_000));
    }

    @Test
    @DisplayName("PATCH /api/v1/room-type-rates/{rateId}: 미존재 id 는 404 RATE_NOT_FOUND")
    void changeReturns404OnMissing() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.change(any())).thenThrow(RateNotFoundException.byId(RateId.newId()));

        var request = new ChangeRoomTypeRateRequest(200_000L, "KRW");

        mockMvc.perform(patch("/api/v1/room-type-rates/rate-missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RATE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/room-type-rates: 범위 결과 리스트를 CommonResponse.data 로 반환")
    void findByRangeReturns200() throws Exception {
        RoomTypeRateResult r1 = new RoomTypeRateResult(
            "rate-1", HOTEL_UUID.toString(), ROOM_TYPE_UUID.toString(),
            LocalDate.of(2026, 6, 1), 150_000L, "KRW");
        RoomTypeRateResult r2 = new RoomTypeRateResult(
            "rate-2", HOTEL_UUID.toString(), ROOM_TYPE_UUID.toString(),
            LocalDate.of(2026, 6, 2), 170_000L, "KRW");
        when(service.findByRange(eq(HOTEL_UUID.toString()), eq(ROOM_TYPE_UUID.toString()),
            eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 2))))
            .thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/v1/room-type-rates")
                .param("hotelId", HOTEL_UUID.toString())
                .param("roomTypeId", ROOM_TYPE_UUID.toString())
                .param("from", "2026-06-01")
                .param("to", "2026-06-02"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value("rate-1"))
            .andExpect(jsonPath("$.data[1].amount").value(170_000));
    }

    @Test
    @DisplayName("GET /api/v1/room-type-rates: from > to 이면 400 VALIDATION_FAILED — 파라미터 순서까지 검증")
    void findByRangeReturns400OnInvertedRange() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        // Controller 가 request param 을 Application 에 (hotelId, roomTypeId, from, to)
        // 순서 그대로 전달해야 IllegalArgumentException 이 발생하는 stub. anyString()/any()
        // 로 풀어두면 Controller 의 파라미터 순서 회귀가 잡히지 않는다.
        when(service.findByRange(
            eq(HOTEL_UUID.toString()),
            eq(ROOM_TYPE_UUID.toString()),
            eq(LocalDate.of(2026, 6, 2)),
            eq(LocalDate.of(2026, 6, 1))))
            .thenThrow(new IllegalArgumentException("from must be <= to"));

        mockMvc.perform(get("/api/v1/room-type-rates")
                .param("hotelId", HOTEL_UUID.toString())
                .param("roomTypeId", ROOM_TYPE_UUID.toString())
                .param("from", "2026-06-02")
                .param("to", "2026-06-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}

package com.reservation.reservation.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.reservation.application.dto.ReservationResult;
import com.reservation.reservation.application.service.CreateReservationApplicationService;
import com.reservation.reservation.domain.exception.InsufficientInventoryException;
import com.reservation.reservation.domain.exception.InventoryNotInitializedException;
import com.reservation.reservation.domain.model.GuestId;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.ReservationStatus;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.service.GuestVerificationPort;
import com.reservation.reservation.domain.service.RoomTypeRateQuotePort;
import com.reservation.reservation.presentation.dto.CreateReservationRequest;
import com.reservation.reservation.presentation.exception.ReservationExceptionHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@Import(ReservationExceptionHandler.class)
class ReservationControllerTest {

    private static final String HOTEL_ID = "01933333-1111-7aaa-9aaa-000000000001";
    private static final String ROOM_TYPE_ID = "01933333-1111-7aaa-9aaa-000000000002";
    private static final String GUEST_ID = "01933333-1111-7aaa-9aaa-000000000003";
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-06-01");
    private static final LocalDate CHECK_OUT = LocalDate.parse("2026-06-03");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    CreateReservationApplicationService service;
    @MockBean
    Clock clock;

    @Test
    @DisplayName("POST /api/v1/reservations: 201 + CommonResponse 래퍼")
    void create201() throws Exception {
        when(service.create(any())).thenReturn(new ReservationResult(
            "01933333-1111-7aaa-9aaa-aaaaaaaaaaaa", ReservationStatus.CONFIRMED,
            300_000L, "KRW"));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(validRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.reservationId").value("01933333-1111-7aaa-9aaa-aaaaaaaaaaaa"))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.totalAmount").value(300_000))
            .andExpect(jsonPath("$.data.currency").value("KRW"));
    }

    @Test
    @DisplayName("guest-service NOT_FOUND → 404 GUEST_NOT_FOUND")
    void guestNotFound404() throws Exception {
        clockStub();
        when(service.create(any())).thenThrow(
            new GuestVerificationPort.GuestNotFoundException(GuestId.of(GUEST_ID)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(validRequest())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("GUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("rate-service NOT_FOUND → 404 RATE_NOT_FOUND")
    void rateNotFound404() throws Exception {
        clockStub();
        when(service.create(any())).thenThrow(
            new RoomTypeRateQuotePort.RoomTypeRateNotFoundException(
                HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID), CHECK_IN));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(validRequest())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RATE_NOT_FOUND"));
    }

    @Test
    @DisplayName("inventory row 부재 → 404 INVENTORY_NOT_INITIALIZED")
    void inventoryNotInitialized404() throws Exception {
        clockStub();
        when(service.create(any())).thenThrow(
            new InventoryNotInitializedException(
                HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID), CHECK_IN));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(validRequest())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INVENTORY_NOT_INITIALIZED"));
    }

    @Test
    @DisplayName("재고 부족 → 409 INSUFFICIENT_INVENTORY")
    void insufficient409() throws Exception {
        clockStub();
        when(service.create(any())).thenThrow(
            new InsufficientInventoryException(new InventoryKey(
                HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID), CHECK_IN)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(validRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
    }

    @Test
    @DisplayName("guest-service UNAVAILABLE → 503 GUEST_SERVICE_UNAVAILABLE")
    void guestUnavailable503() throws Exception {
        clockStub();
        when(service.create(any())).thenThrow(
            new GuestVerificationPort.GuestVerificationUnavailableException(
                GuestId.of(GUEST_ID), new StatusRuntimeException(Status.UNAVAILABLE)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(validRequest())))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("GUEST_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("rate-service UNAVAILABLE → 503 RATE_SERVICE_UNAVAILABLE")
    void rateUnavailable503() throws Exception {
        clockStub();
        when(service.create(any())).thenThrow(
            new RoomTypeRateQuotePort.RateServiceUnavailableException(
                HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID), CHECK_IN,
                new StatusRuntimeException(Status.UNAVAILABLE)));

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(validRequest())))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("RATE_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("잘못된 UUID 는 도메인 VO 생성 단계에서 IllegalArgument → 400 VALIDATION_FAILED")
    void malformedUuid400() throws Exception {
        clockStub();
        // service stub 없음 — 실제 GuestId.of(...) 가 IllegalArgumentException 을 던지는 경로.
        // Application Service 진입 후 첫 줄에서 VO 변환이 일어나면서 실패한다.
        when(service.create(any())).thenAnswer(inv -> {
            // ApplicationService 의 첫 줄을 모사 — VO 변환이 IllegalArgument 를 던짐.
            throw new IllegalArgumentException("Invalid UUID string: not-a-uuid");
        });

        var request = new CreateReservationRequest(
            "not-a-uuid", ROOM_TYPE_ID, GUEST_ID, CHECK_IN, CHECK_OUT, 2);

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("numberOfGuests=0 도 VO 검증 실패로 400 VALIDATION_FAILED 매핑")
    void zeroNumberOfGuests400() throws Exception {
        clockStub();
        when(service.create(any())).thenThrow(
            new IllegalArgumentException("numberOfGuests must be >= 1, was 0"));

        var request = new CreateReservationRequest(
            HOTEL_ID, ROOM_TYPE_ID, GUEST_ID, CHECK_IN, CHECK_OUT, 0);

        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private CreateReservationRequest validRequest() {
        return new CreateReservationRequest(HOTEL_ID, ROOM_TYPE_ID, GUEST_ID,
            CHECK_IN, CHECK_OUT, 2);
    }

    private void clockStub() {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-23T01:00:00Z"));
    }
}

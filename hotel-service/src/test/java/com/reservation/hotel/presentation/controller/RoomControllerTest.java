package com.reservation.hotel.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.hotel.application.dto.RoomResult;
import com.reservation.hotel.application.service.RoomApplicationService;
import com.reservation.hotel.domain.exception.DuplicateRoomNumberException;
import com.reservation.hotel.domain.exception.InvalidRoomStateTransitionException;
import com.reservation.hotel.domain.exception.RoomNotFoundException;
import com.reservation.hotel.domain.exception.RoomTypeHotelMismatchException;
import com.reservation.hotel.domain.model.Floor;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomId;
import com.reservation.hotel.domain.model.RoomNumber;
import com.reservation.hotel.domain.model.RoomStatus;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.presentation.dto.RegisterRoomRequest;
import com.reservation.hotel.presentation.dto.UpdateRoomRequest;
import com.reservation.hotel.presentation.exception.HotelExceptionHandler;
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
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@Import(HotelExceptionHandler.class)
class RoomControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    RoomApplicationService service;
    @MockBean
    Clock clock;

    @Test
    @DisplayName("POST /api/v1/rooms: 201 + Location + body")
    void registerReturns201() throws Exception {
        RoomResult result = new RoomResult("room-1", "h-1", "rt-1", 3, "301", "ACTIVE");
        when(service.register(any())).thenReturn(result);
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));

        var request = new RegisterRoomRequest("h-1", "rt-1", 3, "301");

        mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/rooms/room-1"))
            .andExpect(jsonPath("$.id").value("room-1"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/rooms: RoomType이 다른 호텔 소속이면 409")
    void registerReturns409OnHotelMismatch() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any()))
            .thenThrow(new RoomTypeHotelMismatchException(HotelId.newId(), RoomTypeId.newId(), HotelId.newId()));

        var request = new RegisterRoomRequest("h-1", "rt-1", 3, "301");

        mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROOM_TYPE_HOTEL_MISMATCH"));
    }

    @Test
    @DisplayName("POST /api/v1/rooms: 중복 번호는 409")
    void registerReturns409OnDuplicate() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any()))
            .thenThrow(new DuplicateRoomNumberException(HotelId.newId(), new Floor(3), new RoomNumber("301")));

        var request = new RegisterRoomRequest("h-1", "rt-1", 3, "301");

        mockMvc.perform(post("/api/v1/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_ROOM_NUMBER"));
    }

    @Test
    @DisplayName("PATCH /api/v1/rooms/{id}: 200 + 바디")
    void updateReturns200() throws Exception {
        RoomResult result = new RoomResult("room-1", "h-1", "rt-2", 3, "301", "ACTIVE");
        when(service.updateRoomType(any())).thenReturn(result);

        var request = new UpdateRoomRequest("rt-2");

        mockMvc.perform(patch("/api/v1/rooms/room-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roomTypeId").value("rt-2"));
    }

    @Test
    @DisplayName("DELETE /api/v1/rooms/{id}: 204 No Content")
    void deactivateReturns204() throws Exception {
        doNothing().when(service).deactivate(anyString());

        mockMvc.perform(delete("/api/v1/rooms/room-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/rooms/{id}: 404 매핑")
    void findByIdReturns404() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.findById(anyString())).thenThrow(new RoomNotFoundException(RoomId.newId()));

        mockMvc.perform(get("/api/v1/rooms/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/v1/rooms/{id}: 이미 전이 제약 위반이면 409 INVALID_ROOM_STATE_TRANSITION")
    void deactivateReturns409OnInvalidState() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        doThrow(new InvalidRoomStateTransitionException(RoomId.newId(), RoomStatus.DEACTIVATED, "startMaintenance"))
            .when(service).deactivate(anyString());

        mockMvc.perform(delete("/api/v1/rooms/room-1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_ROOM_STATE_TRANSITION"));
    }
}

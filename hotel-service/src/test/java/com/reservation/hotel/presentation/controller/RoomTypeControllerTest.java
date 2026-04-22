package com.reservation.hotel.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.hotel.application.dto.RoomTypeResult;
import com.reservation.hotel.application.service.RoomTypeApplicationService;
import com.reservation.hotel.domain.exception.DuplicateRoomTypeNameException;
import com.reservation.hotel.domain.exception.HotelNotFoundException;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeName;
import com.reservation.hotel.presentation.dto.RegisterRoomTypeRequest;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomTypeController.class)
@Import(HotelExceptionHandler.class)
class RoomTypeControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    RoomTypeApplicationService service;
    @MockBean
    Clock clock;

    @Test
    @DisplayName("POST /api/v1/room-types: 201 + Location")
    void registerReturns201() throws Exception {
        RoomTypeResult result = new RoomTypeResult("rt-1", "h-1", "Standard", 2);
        when(service.register(any())).thenReturn(result);
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));

        mockMvc.perform(post("/api/v1/room-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new RegisterRoomTypeRequest("h-1", "Standard", 2))))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/room-types/rt-1"))
            .andExpect(jsonPath("$.id").value("rt-1"));
    }

    @Test
    @DisplayName("POST /api/v1/room-types: Hotel 미존재 시 404 HOTEL_NOT_FOUND")
    void registerReturns404WhenHotelMissing() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any())).thenThrow(new HotelNotFoundException(HotelId.newId()));

        mockMvc.perform(post("/api/v1/room-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new RegisterRoomTypeRequest("h-1", "Standard", 2))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/room-types: 중복 이름은 409 DUPLICATE_ROOM_TYPE_NAME")
    void registerReturns409OnDuplicate() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.register(any()))
            .thenThrow(new DuplicateRoomTypeNameException(HotelId.newId(), new RoomTypeName("Standard")));

        mockMvc.perform(post("/api/v1/room-types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new RegisterRoomTypeRequest("h-1", "Standard", 2))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_ROOM_TYPE_NAME"));
    }

    @Test
    @DisplayName("GET /api/v1/room-types?hotelId=: 리스트 반환")
    void listByHotelReturnsOk() throws Exception {
        when(service.listByHotel(anyString())).thenReturn(List.of(
            new RoomTypeResult("rt-1", "h-1", "Standard", 2),
            new RoomTypeResult("rt-2", "h-1", "Deluxe", 3)
        ));

        mockMvc.perform(get("/api/v1/room-types").param("hotelId", "h-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].name").value("Deluxe"));
    }
}

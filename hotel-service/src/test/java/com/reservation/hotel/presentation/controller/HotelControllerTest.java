package com.reservation.hotel.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.hotel.application.dto.HotelResult;
import com.reservation.hotel.application.service.HotelApplicationService;
import com.reservation.hotel.domain.exception.HotelNotFoundException;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.presentation.dto.RegisterHotelRequest;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotelController.class)
@Import(HotelExceptionHandler.class)
class HotelControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    HotelApplicationService service;
    @MockBean
    Clock clock;

    @Test
    @DisplayName("POST /api/v1/hotels: 201 + CommonResponse 래퍼 body")
    void registerReturns201() throws Exception {
        HotelResult result = new HotelResult("id-1", "A", "1 St", "Seoul", "KR", 4, Set.of("WIFI"));
        when(service.register(any())).thenReturn(result);
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));

        var request = new RegisterHotelRequest("A", "1 St", "Seoul", "KR", 4, Set.of("WIFI"));

        mockMvc.perform(post("/api/v1/hotels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value("id-1"))
            .andExpect(jsonPath("$.data.address.city").value("Seoul"));
    }

    @Test
    @DisplayName("GET /api/v1/hotels/{id}: 미존재 시 404")
    void findByIdReturns404WhenMissing() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(service.findById(any())).thenThrow(new HotelNotFoundException(HotelId.newId()));

        mockMvc.perform(get("/api/v1/hotels/018f4a9b-2c4d-7c34-8e9a-000000000000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("HOTEL_NOT_FOUND"));
    }
}

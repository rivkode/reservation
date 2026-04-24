package com.reservation.hotel.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.hotel.application.dto.AvailabilityQuery;
import com.reservation.hotel.application.dto.AvailabilityResult;
import com.reservation.hotel.application.service.AvailabilityQueryApplicationService;
import com.reservation.hotel.presentation.dto.AvailabilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * FR-H-06 — 가용성 조회 REST API. PRD §8.2 스펙을 그대로 따르며 Redis Read Model 만
 * 조회한다 (gRPC fallback 금지 — ADR 0004). Redis 장애 시 503 응답은
 * {@code HotelExceptionHandler} 가 {@code DataAccessException} 을 매핑.
 */
@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityQueryApplicationService availabilityQueryApplicationService;

    @GetMapping
    public ResponseEntity<CommonResponse<AvailabilityResponse>> query(
        @RequestParam String hotelId,
        @RequestParam String roomTypeId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {

        AvailabilityResult result = availabilityQueryApplicationService.query(
            new AvailabilityQuery(hotelId, roomTypeId, checkIn, checkOut));

        return ResponseEntity.ok(CommonResponse.of(AvailabilityResponse.of(result)));
    }
}

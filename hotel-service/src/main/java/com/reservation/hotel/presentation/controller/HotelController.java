package com.reservation.hotel.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.hotel.application.dto.HotelResult;
import com.reservation.hotel.application.service.HotelApplicationService;
import com.reservation.hotel.presentation.dto.HotelResponse;
import com.reservation.hotel.presentation.dto.RegisterHotelRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelApplicationService hotelApplicationService;

    @PostMapping
    public ResponseEntity<CommonResponse<HotelResponse>> register(@RequestBody RegisterHotelRequest request) {
        HotelResult result = hotelApplicationService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(HotelResponse.of(result)));
    }

    @GetMapping("/{hotelId}")
    public ResponseEntity<CommonResponse<HotelResponse>> findById(@PathVariable String hotelId) {
        HotelResult result = hotelApplicationService.findById(hotelId);
        return ResponseEntity.ok(CommonResponse.of(HotelResponse.of(result)));
    }
}

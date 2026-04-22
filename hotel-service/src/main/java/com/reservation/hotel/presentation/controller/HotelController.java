package com.reservation.hotel.presentation.controller;

import com.reservation.hotel.application.dto.HotelResult;
import com.reservation.hotel.application.service.HotelApplicationService;
import com.reservation.hotel.presentation.dto.HotelResponse;
import com.reservation.hotel.presentation.dto.RegisterHotelRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    private final HotelApplicationService hotelApplicationService;

    public HotelController(HotelApplicationService hotelApplicationService) {
        this.hotelApplicationService = hotelApplicationService;
    }

    @PostMapping
    public ResponseEntity<HotelResponse> register(@RequestBody RegisterHotelRequest request) {
        HotelResult result = hotelApplicationService.register(request.toCommand());
        URI location = UriComponentsBuilder.fromPath("/api/v1/hotels/{id}")
            .buildAndExpand(result.id())
            .toUri();
        return ResponseEntity.created(location).body(HotelResponse.of(result));
    }

    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelResponse> findById(@PathVariable String hotelId) {
        return ResponseEntity.ok(HotelResponse.of(hotelApplicationService.findById(hotelId)));
    }
}

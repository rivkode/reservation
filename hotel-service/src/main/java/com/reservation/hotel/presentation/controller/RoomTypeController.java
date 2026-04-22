package com.reservation.hotel.presentation.controller;

import com.reservation.hotel.application.dto.RoomTypeResult;
import com.reservation.hotel.application.service.RoomTypeApplicationService;
import com.reservation.hotel.presentation.dto.RegisterRoomTypeRequest;
import com.reservation.hotel.presentation.dto.RoomTypeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/room-types")
public class RoomTypeController {

    private final RoomTypeApplicationService roomTypeApplicationService;

    public RoomTypeController(RoomTypeApplicationService roomTypeApplicationService) {
        this.roomTypeApplicationService = roomTypeApplicationService;
    }

    @PostMapping
    public ResponseEntity<RoomTypeResponse> register(@RequestBody RegisterRoomTypeRequest request) {
        RoomTypeResult result = roomTypeApplicationService.register(request.toCommand());
        URI location = UriComponentsBuilder.fromPath("/api/v1/room-types/{id}")
            .buildAndExpand(result.id())
            .toUri();
        return ResponseEntity.created(location).body(RoomTypeResponse.of(result));
    }

    @GetMapping("/{roomTypeId}")
    public ResponseEntity<RoomTypeResponse> findById(@PathVariable String roomTypeId) {
        return ResponseEntity.ok(RoomTypeResponse.of(roomTypeApplicationService.findById(roomTypeId)));
    }

    @GetMapping
    public ResponseEntity<List<RoomTypeResponse>> listByHotel(@RequestParam("hotelId") String hotelId) {
        List<RoomTypeResponse> response = roomTypeApplicationService.listByHotel(hotelId).stream()
            .map(RoomTypeResponse::of)
            .toList();
        return ResponseEntity.ok(response);
    }
}

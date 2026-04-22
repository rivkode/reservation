package com.reservation.hotel.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.hotel.application.dto.RoomTypeResult;
import com.reservation.hotel.application.service.RoomTypeApplicationService;
import com.reservation.hotel.presentation.dto.RegisterRoomTypeRequest;
import com.reservation.hotel.presentation.dto.RoomTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeApplicationService roomTypeApplicationService;

    @PostMapping
    public ResponseEntity<CommonResponse<RoomTypeResponse>> register(@RequestBody RegisterRoomTypeRequest request) {
        RoomTypeResult result = roomTypeApplicationService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(RoomTypeResponse.of(result)));
    }

    @GetMapping("/{roomTypeId}")
    public ResponseEntity<CommonResponse<RoomTypeResponse>> findById(@PathVariable String roomTypeId) {
        RoomTypeResult result = roomTypeApplicationService.findById(roomTypeId);
        return ResponseEntity.ok(CommonResponse.of(RoomTypeResponse.of(result)));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<List<RoomTypeResponse>>> listByHotel(@RequestParam("hotelId") String hotelId) {
        List<RoomTypeResponse> response = roomTypeApplicationService.listByHotel(hotelId).stream()
            .map(RoomTypeResponse::of)
            .toList();
        return ResponseEntity.ok(CommonResponse.of(response));
    }
}

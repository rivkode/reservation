package com.reservation.hotel.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.hotel.application.dto.RoomResult;
import com.reservation.hotel.application.service.RoomApplicationService;
import com.reservation.hotel.presentation.dto.RegisterRoomRequest;
import com.reservation.hotel.presentation.dto.RoomResponse;
import com.reservation.hotel.presentation.dto.UpdateRoomRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomApplicationService roomApplicationService;

    @PostMapping
    public ResponseEntity<CommonResponse<RoomResponse>> register(@RequestBody RegisterRoomRequest request) {
        RoomResult result = roomApplicationService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(RoomResponse.of(result)));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<CommonResponse<RoomResponse>> findById(@PathVariable String roomId) {
        return ResponseEntity.ok(CommonResponse.of(RoomResponse.of(roomApplicationService.findById(roomId))));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<List<RoomResponse>>> listByHotel(@RequestParam("hotelId") String hotelId) {
        List<RoomResponse> response = roomApplicationService.listByHotel(hotelId).stream()
            .map(RoomResponse::of)
            .toList();
        return ResponseEntity.ok(CommonResponse.of(response));
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<CommonResponse<RoomResponse>> updateRoomType(@PathVariable String roomId,
                                                                       @RequestBody UpdateRoomRequest request) {
        RoomResult result = roomApplicationService.updateRoomType(request.toCommand(roomId));
        return ResponseEntity.ok(CommonResponse.of(RoomResponse.of(result)));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<CommonResponse<Void>> deactivate(@PathVariable String roomId) {
        roomApplicationService.deactivate(roomId);
        return ResponseEntity.ok(CommonResponse.of(null));
    }
}

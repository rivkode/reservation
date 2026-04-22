package com.reservation.hotel.presentation.controller;

import com.reservation.hotel.application.dto.RoomResult;
import com.reservation.hotel.application.service.RoomApplicationService;
import com.reservation.hotel.presentation.dto.RegisterRoomRequest;
import com.reservation.hotel.presentation.dto.RoomResponse;
import com.reservation.hotel.presentation.dto.UpdateRoomRequest;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomApplicationService roomApplicationService;

    public RoomController(RoomApplicationService roomApplicationService) {
        this.roomApplicationService = roomApplicationService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> register(@RequestBody RegisterRoomRequest request) {
        RoomResult result = roomApplicationService.register(request.toCommand());
        URI location = UriComponentsBuilder.fromPath("/api/v1/rooms/{id}")
            .buildAndExpand(result.id())
            .toUri();
        return ResponseEntity.created(location).body(RoomResponse.of(result));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> findById(@PathVariable String roomId) {
        return ResponseEntity.ok(RoomResponse.of(roomApplicationService.findById(roomId)));
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> listByHotel(@RequestParam("hotelId") String hotelId) {
        List<RoomResponse> response = roomApplicationService.listByHotel(hotelId).stream()
            .map(RoomResponse::of)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<RoomResponse> updateRoomType(@PathVariable String roomId,
                                                       @RequestBody UpdateRoomRequest request) {
        RoomResult result = roomApplicationService.updateRoomType(request.toCommand(roomId));
        return ResponseEntity.ok(RoomResponse.of(result));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deactivate(@PathVariable String roomId) {
        roomApplicationService.deactivate(roomId);
        return ResponseEntity.noContent().build();
    }
}

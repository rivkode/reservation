package com.reservation.guest.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.guest.application.dto.GuestResult;
import com.reservation.guest.application.service.GuestApplicationService;
import com.reservation.guest.presentation.dto.ChangeGuestRequest;
import com.reservation.guest.presentation.dto.GuestResponse;
import com.reservation.guest.presentation.dto.RegisterGuestRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 투숙객 관리 REST. FR-G-01 등록 · FR-G-02 조회 · FR-G-03 변경.
 *
 * <p>gRPC 서비스 {@code GuestService/GetGuest · BatchGetGuests} 는
 * {@code infrastructure/grpc/server/GuestGrpcService} 가 별도로 제공한다 (서비스간
 * 통신은 REST 를 거치지 않는다).
 */
@RestController
@RequestMapping("/api/v1/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestApplicationService service;

    @PostMapping
    public ResponseEntity<CommonResponse<GuestResponse>> register(
        @RequestBody RegisterGuestRequest request) {
        GuestResult result = service.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(GuestResponse.of(result)));
    }

    @GetMapping("/{guestId}")
    public ResponseEntity<CommonResponse<GuestResponse>> findById(@PathVariable String guestId) {
        GuestResult result = service.findById(guestId);
        return ResponseEntity.ok(CommonResponse.of(GuestResponse.of(result)));
    }

    @PatchMapping("/{guestId}")
    public ResponseEntity<CommonResponse<GuestResponse>> change(
        @PathVariable String guestId,
        @RequestBody ChangeGuestRequest request) {
        GuestResult result = service.change(request.toCommand(guestId));
        return ResponseEntity.ok(CommonResponse.of(GuestResponse.of(result)));
    }
}

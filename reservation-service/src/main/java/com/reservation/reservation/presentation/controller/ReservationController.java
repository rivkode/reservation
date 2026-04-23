package com.reservation.reservation.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.reservation.application.dto.ReservationResult;
import com.reservation.reservation.application.service.CreateReservationApplicationService;
import com.reservation.reservation.presentation.dto.CreateReservationRequest;
import com.reservation.reservation.presentation.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final CreateReservationApplicationService createReservationApplicationService;

    @PostMapping
    public ResponseEntity<CommonResponse<ReservationResponse>> create(
        @RequestBody CreateReservationRequest request) {
        ReservationResult result = createReservationApplicationService.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(ReservationResponse.of(result)));
    }
}

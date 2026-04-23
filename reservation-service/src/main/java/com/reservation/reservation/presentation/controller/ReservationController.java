package com.reservation.reservation.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.reservation.application.dto.CancelReservationResult;
import com.reservation.reservation.application.dto.ReservationResult;
import com.reservation.reservation.application.service.CancelReservationApplicationService;
import com.reservation.reservation.application.service.CreateReservationApplicationService;
import com.reservation.reservation.domain.model.ReservationId;
import com.reservation.reservation.presentation.dto.CancelReservationResponse;
import com.reservation.reservation.presentation.dto.CreateReservationRequest;
import com.reservation.reservation.presentation.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final CreateReservationApplicationService createReservationApplicationService;
    private final CancelReservationApplicationService cancelReservationApplicationService;

    @PostMapping
    public ResponseEntity<CommonResponse<ReservationResponse>> create(
        @RequestBody CreateReservationRequest request) {
        ReservationResult result = createReservationApplicationService.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(ReservationResponse.of(result)));
    }

    /**
     * 사용자 취소 API. PRD §13 Q6 결정에 따라 인증/소유권 검증 (호출자가 해당 예약의
     * 투숙객인지 확인) 은 본 PR 범위 외 — 별도 인증 PRD (OAuth2/JWT/Role) 도입 시
     * 본 메서드 진입 전 단계에서 처리한다. 그때까지 임시 운영은 외부 노출 금지 + 게이트웨이
     * 또는 IP 제한으로 막는다.
     *
     * <p>TODO(auth-pr): 인증 PRD 머지 후 X-Guest-Id (또는 SecurityContext) 와
     * reservation.guestId 일치 검증 추가.
     */
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<CommonResponse<CancelReservationResponse>> cancel(
        @PathVariable String reservationId) {
        CancelReservationResult result = cancelReservationApplicationService
            .cancelByUser(ReservationId.of(reservationId));
        return ResponseEntity.ok(CommonResponse.of(CancelReservationResponse.of(result)));
    }
}

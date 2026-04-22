package com.reservation.rate.presentation.controller;

import com.reservation.common.presentation.CommonResponse;
import com.reservation.rate.application.dto.RoomTypeRateResult;
import com.reservation.rate.application.service.RoomTypeRateApplicationService;
import com.reservation.rate.presentation.dto.ChangeRoomTypeRateRequest;
import com.reservation.rate.presentation.dto.RegisterRoomTypeRateRequest;
import com.reservation.rate.presentation.dto.RoomTypeRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 요금 관리 REST. FR-R-01 · FR-R-02 · FR-R-03.
 *
 * <p>공개 엔드포인트는 자연키 위주로 설계됐다 — surrogate {@code rateId} 는 등록 응답에서
 * 돌려받은 관리자 클라이언트만 보유한다. 따라서 단건 GET({@code /rateId}) 는 제공하지
 * 않는다 (자연키 조회로 충분하며, 향후 gRPC {@code GetRoomTypeRate} 가 조회 경로 담당).
 */
@RestController
@RequestMapping("/api/v1/room-type-rates")
@RequiredArgsConstructor
public class RoomTypeRateController {

    private final RoomTypeRateApplicationService service;

    @PostMapping
    public ResponseEntity<CommonResponse<RoomTypeRateResponse>> register(
        @RequestBody RegisterRoomTypeRateRequest request) {
        RoomTypeRateResult result = service.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(RoomTypeRateResponse.of(result)));
    }

    @PatchMapping("/{rateId}")
    public ResponseEntity<CommonResponse<RoomTypeRateResponse>> change(
        @PathVariable String rateId,
        @RequestBody ChangeRoomTypeRateRequest request) {
        RoomTypeRateResult result = service.change(request.toCommand(rateId));
        return ResponseEntity.ok(CommonResponse.of(RoomTypeRateResponse.of(result)));
    }

    @GetMapping
    public ResponseEntity<CommonResponse<List<RoomTypeRateResponse>>> findByRange(
        @RequestParam("hotelId") String hotelId,
        @RequestParam("roomTypeId") String roomTypeId,
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<RoomTypeRateResponse> response = service.findByRange(hotelId, roomTypeId, from, to).stream()
            .map(RoomTypeRateResponse::of)
            .toList();
        return ResponseEntity.ok(CommonResponse.of(response));
    }
}

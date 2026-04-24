package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.AvailabilityResult;

import java.time.Instant;
import java.util.List;

/**
 * 가용성 조회 응답 (PRD §8.2). {@code staleUntil} = {@code min(updatedAt) + 30s} — 이 Instant
 * 가 지나기 전에는 본 응답이 ADR 0004 허용 범위 (≤ 30s stale) 안에 있음을 뜻한다. 이후에는
 * 클라이언트가 재조회하는 것을 권장. {@code availability} 가 비어있으면 {@code staleUntil} 이
 * 누락되어 응답에 포함되지 않는다 (NON_NULL 직렬화 정책).
 */
public record AvailabilityResponse(String hotelId,
                                   String roomTypeId,
                                   List<DailyAvailabilityResponse> availability,
                                   Instant staleUntil) {

    public static AvailabilityResponse of(AvailabilityResult result) {
        List<DailyAvailabilityResponse> days = result.availability().stream()
            .map(d -> new DailyAvailabilityResponse(d.date(), d.available(), d.total()))
            .toList();
        return new AvailabilityResponse(result.hotelId(), result.roomTypeId(), days, result.staleUntil());
    }
}

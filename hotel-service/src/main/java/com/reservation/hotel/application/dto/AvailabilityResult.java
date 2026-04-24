package com.reservation.hotel.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 가용성 조회 결과.
 *
 * <p>{@code availability} 는 날짜 오름차순이며 value 부재 날짜는 포함되지 않는다.
 * {@code staleUntil} 산식 = 응답에 포함된 모든 스냅샷의 가장 오래된 {@code updatedAt + 30s}.
 * 의미: <strong>이 Instant 가 지나기 전에는 본 응답이 ADR 0004 허용 stale (≤ 30s) 범위
 * 내라는 보장</strong>. 이후로는 클라이언트가 재조회를 권장받는다. 조회 결과가 비어있으면
 * {@code null} 로 반환되어 stale 판정 자체가 불가함을 표현.
 */
public record AvailabilityResult(String hotelId,
                                 String roomTypeId,
                                 List<DailyAvailability> availability,
                                 Instant staleUntil) {

    public AvailabilityResult {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        Objects.requireNonNull(availability, "availability");
        availability = List.copyOf(availability);
    }
}

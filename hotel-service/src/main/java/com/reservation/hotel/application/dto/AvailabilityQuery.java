package com.reservation.hotel.application.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 가용성 조회 입력 Query. Presentation 이 REST 쿼리 파라미터를 검증/파싱한 뒤 본 record 로
 * Application Service 에 전달한다. Service 는 여기서 추가 도메인 규칙(날짜 순서, 최대
 * 범위) 을 검증한다.
 */
public record AvailabilityQuery(String hotelId,
                                String roomTypeId,
                                LocalDate checkIn,
                                LocalDate checkOut) {

    public AvailabilityQuery {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
    }
}

package com.reservation.hotel.presentation.dto;

import com.reservation.hotel.application.dto.RegisterHotelCommand;

import java.util.Set;

/**
 * HTTP 요청 포맷. 필드 제약은 도메인 VO (HotelName · HotelAddress · StarRating · Amenity)
 * 가 재확인하므로 본 DTO 에서는 jakarta.validation 을 사용하지 않는다.
 * (validation-api 의존성 추가는 사용자 승인이 필요해 현재 PR 범위 외.)
 */
public record RegisterHotelRequest(
    String name,
    String addressStreet,
    String addressCity,
    String addressCountry,
    int starRating,
    Set<String> amenities
) {

    public RegisterHotelCommand toCommand() {
        return new RegisterHotelCommand(
            name, addressStreet, addressCity, addressCountry,
            starRating, amenities == null ? Set.of() : amenities
        );
    }
}

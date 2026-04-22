package com.reservation.hotel.domain.model;

import java.util.Objects;

/**
 * Hotel 주소 VO. street / city / country 세 필드로 구조화되며 프로토콜
 * 호환(proto {@code HotelAddress}) 을 위해 동일한 구성을 따른다.
 *
 * <p>ddd-architect C1 결정으로 단일 string 이 아닌 세 필드로 쪼개 FR-H-02 의
 * 도시 / 국가 필터링을 인덱스 친화적으로 만들 근거를 도메인에서 확보한다.
 * 국가 코드는 향후 ISO-3166 CountryCode VO 로 확장 가능.
 */
public record HotelAddress(String street, String city, String country) {

    public static final int STREET_MAX = 200;
    public static final int CITY_MAX = 100;
    public static final int COUNTRY_MAX = 100;

    public HotelAddress {
        street = requireNonBlank(street, "street", STREET_MAX);
        city = requireNonBlank(city, "city", CITY_MAX);
        country = requireNonBlank(country, "country", COUNTRY_MAX);
    }

    private static String requireNonBlank(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(
                field + " must be at most " + max + " characters, was " + trimmed.length());
        }
        return trimmed;
    }
}

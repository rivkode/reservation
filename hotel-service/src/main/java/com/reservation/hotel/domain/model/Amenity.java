package com.reservation.hotel.domain.model;

/**
 * Hotel 이 제공하는 편의시설. 초기 집합은 PRD 의 공통 사례를 수용하고, 새로운
 * 값이 필요하면 enum 에 추가한 뒤 서비스 모두의 마이그레이션 범위를 확인한다.
 *
 * <p>enum 으로 제한해 자유 문자열 허용에서 오는 표기 혼선(`wifi` / `WiFi` /
 * `Wi-Fi`)을 차단한다. DB 저장은 {@code VARCHAR(32)} 에 name() 그대로.
 */
public enum Amenity {
    WIFI,
    PARKING,
    POOL,
    GYM,
    RESTAURANT,
    BAR,
    SPA,
    AIR_CONDITIONING,
    LAUNDRY,
    PET_FRIENDLY;

    public static final int DB_COLUMN_MAX = 32;
}

package com.reservation.rate.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * rate-service 가 외부 hotel-service 를 가리키는 참조 값 객체.
 *
 * <p>Database per Service 원칙상 rate-service 는 hotel-service 가 발급한 UUID 를 그대로
 * 받아 저장할 뿐 존재성을 동기 검증하지 않는다. 고아 레코드는 후속 PR 에서
 * {@code RoomDeletedEvent} 구독으로 정리한다 (PRD FR-H-05 연동, 이번 PR 범위 외).
 *
 * <p>rate-service 내부에서 타입으로 의미를 드러내기 위해 String 대신 VO 로 감싸며,
 * 동일 이유로 hotel-service 의 {@code com.reservation.hotel.domain.model.HotelId} 와는
 * **의도적으로 분리된** 타입이다 — 서비스간 도메인 모델 공유 금지 (CLAUDE.md 원칙 #1).
 */
public record HotelId(UUID value) {

    public HotelId {
        Objects.requireNonNull(value, "value");
    }

    public static HotelId of(UUID value) {
        return new HotelId(value);
    }

    public static HotelId of(String value) {
        Objects.requireNonNull(value, "value");
        return new HotelId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

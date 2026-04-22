package com.reservation.rate.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * rate-service 가 외부 hotel-service 의 RoomType 을 가리키는 참조 값 객체.
 *
 * <p>{@link HotelId} 와 같은 설계 이유로 별도 타입으로 정의된다 — 서비스간 도메인
 * 모델 공유 금지 (CLAUDE.md 원칙 #1). 존재성 검증은 이벤트 구독 기반으로 후속
 * PR 에서 처리.
 */
public record RoomTypeId(UUID value) {

    public RoomTypeId {
        Objects.requireNonNull(value, "value");
    }

    public static RoomTypeId of(UUID value) {
        return new RoomTypeId(value);
    }

    public static RoomTypeId of(String value) {
        Objects.requireNonNull(value, "value");
        return new RoomTypeId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

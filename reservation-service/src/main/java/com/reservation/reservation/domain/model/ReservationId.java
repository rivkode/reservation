package com.reservation.reservation.domain.model;

import com.reservation.common.domain.DomainId;
import com.reservation.common.domain.UuidV7;

import java.util.Objects;
import java.util.UUID;

/**
 * 예약 식별자 VO. reservation-service 가 발급하는 SoT 식별자로 UUID v7 을 사용한다.
 *
 * <p>v7 의 lexicographic time-ordering 성질이 {@code reservation} 테이블의 PK 인덱스
 * 삽입 성능과 운영 로그에서의 시간순 추적성을 동시에 만족시킨다 (PRD §11.2 · UuidV7
 * JavaDoc 참조).
 *
 * <p>외부에서 식별자가 주입되는 {@link HotelId}/{@link RoomTypeId}/{@link GuestId} 와
 * 달리 본 VO 는 {@link #newId()} 로 자체 생성한다 — 예약 생성이 본 서비스의 책임이다.
 */
public record ReservationId(UUID value) implements DomainId {

    public ReservationId {
        Objects.requireNonNull(value, "value");
    }

    public static ReservationId newId() {
        return new ReservationId(UuidV7.create());
    }

    public static ReservationId of(UUID value) {
        return new ReservationId(value);
    }

    public static ReservationId of(String value) {
        Objects.requireNonNull(value, "value");
        return new ReservationId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

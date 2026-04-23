package com.reservation.reservation.domain.model;

import com.reservation.common.domain.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * guest-service 가 발급한 Guest 식별자를 reservation-service 에서 보유하기 위한 VO.
 *
 * <p>예약 생성 요청의 {@code guestId} 문자열을 {@link #of(String)} 로 파싱해 타입
 * 안정성과 형식 검증을 동시에 얻는다. 검증 실패는 {@link IllegalArgumentException}
 * 으로 전파되어 Presentation 계층이 400 으로 매핑한다.
 *
 * <p>외부 SoT(guest-service) 가 발급한 식별자만 사용하므로 자체 생성 팩토리는 두지
 * 않는다 — {@link HotelId} · {@link RoomTypeId} 와 같은 정책.
 */
public record GuestId(UUID value) implements DomainId {

    public GuestId {
        Objects.requireNonNull(value, "value");
    }

    public static GuestId of(UUID value) {
        return new GuestId(value);
    }

    public static GuestId of(String value) {
        Objects.requireNonNull(value, "value");
        return new GuestId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

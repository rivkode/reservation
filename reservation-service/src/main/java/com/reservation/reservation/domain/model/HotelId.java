package com.reservation.reservation.domain.model;

import com.reservation.common.domain.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * hotel-service 가 발급한 Hotel 식별자를 reservation-service 맥락에서 보유하기 위한 VO.
 *
 * <p>reservation-service 는 Hotel 을 생성하지 않으며(SoT 는 hotel-service), hotel-events
 * 로 수신한 {@code hotelId} 문자열을 {@link #of(String)} 로 파싱해 타입 안정성을 얻는다.
 * 잘못된 형식은 즉시 {@link IllegalArgumentException} 으로 거부해 poison event 를 조기에
 * 드러낸다.
 */
public record HotelId(UUID value) implements DomainId {

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

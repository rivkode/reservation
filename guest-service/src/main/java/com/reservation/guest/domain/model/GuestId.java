package com.reservation.guest.domain.model;

import com.reservation.common.domain.DomainId;
import com.reservation.common.domain.UuidV7;

import java.util.Objects;
import java.util.UUID;

/**
 * Guest Aggregate 식별자. UUID v7 값을 보유한다.
 *
 * <p>{@link #newId()} 로 신규 발급, 외부 입력(HTTP · gRPC) 은 {@link #of(String)} 로
 * 복원. 각 정규 경로는 null 을 거부한다.
 */
public record GuestId(UUID value) implements DomainId {

    public GuestId {
        Objects.requireNonNull(value, "value");
    }

    public static GuestId newId() {
        return new GuestId(UuidV7.create());
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

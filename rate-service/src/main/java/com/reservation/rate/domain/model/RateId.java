package com.reservation.rate.domain.model;

import com.reservation.common.domain.DomainId;
import com.reservation.common.domain.UuidV7;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link RoomTypeRate} Aggregate 의 영속성 surrogate 식별자.
 *
 * <p>도메인 자연키는 {@code (hotelId, roomTypeId, date)} 이지만, DB 매핑 단순화와
 * 단건 갱신(PATCH) 경로의 URL 안정성을 위해 UUID v7 을 보조 식별자로 사용한다.
 * 공개 도메인 이벤트 · Kafka payload 에는 자연키 필드만 실리며 rateId 는 외부에
 * 노출되지 않는다 (Vernon IDDD Ch.10 — surrogate 식별자는 내부 참조 용도).
 */
public record RateId(UUID value) implements DomainId {

    public RateId {
        Objects.requireNonNull(value, "value");
    }

    public static RateId newId() {
        return new RateId(UuidV7.create());
    }

    public static RateId of(UUID value) {
        return new RateId(value);
    }

    public static RateId of(String value) {
        Objects.requireNonNull(value, "value");
        return new RateId(UUID.fromString(value));
    }

    public String asString() {
        return value.toString();
    }
}

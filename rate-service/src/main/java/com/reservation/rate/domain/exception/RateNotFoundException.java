package com.reservation.rate.domain.exception;

import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.RateId;
import com.reservation.rate.domain.model.RoomTypeId;

import java.time.LocalDate;

/**
 * 요금 레코드를 찾지 못했을 때 발생. 공개 API 는 HTTP 404 로 매핑된다.
 *
 * <p>surrogate key {@link RateId} 기반 조회와 자연키 {@code (hotelId, roomTypeId, date)}
 * 기반 조회 두 경로 모두에서 발생할 수 있어 정적 팩토리로 분기한다.
 */
public class RateNotFoundException extends RuntimeException {

    public RateNotFoundException(String message) {
        super(message);
    }

    public static RateNotFoundException byId(RateId id) {
        return new RateNotFoundException("RoomTypeRate not found: id=" + id.asString());
    }

    public static RateNotFoundException byNaturalKey(HotelId hotelId, RoomTypeId roomTypeId, LocalDate date) {
        return new RateNotFoundException(
            "RoomTypeRate not found: hotelId=" + hotelId.asString()
                + ", roomTypeId=" + roomTypeId.asString()
                + ", date=" + date);
    }
}

package com.reservation.rate.domain.exception;

import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.RoomTypeId;

import java.time.LocalDate;

/**
 * 자연키 {@code (hotelId, roomTypeId, date)} 충돌 시 발생. 공개 API 는 HTTP 409 로 매핑.
 *
 * <p>DB UNIQUE 제약이 race condition 시 최종 방어선이지만, 정상 경로에서의 예측 가능한
 * 충돌은 본 예외로 전환해 사용자에게 명확한 에러 메시지를 돌려준다.
 */
public class DuplicateRateException extends RuntimeException {

    public DuplicateRateException(HotelId hotelId, RoomTypeId roomTypeId, LocalDate date) {
        super("RoomTypeRate already exists: hotelId=" + hotelId.asString()
            + ", roomTypeId=" + roomTypeId.asString()
            + ", date=" + date);
    }
}

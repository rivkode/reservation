package com.reservation.hotel.domain.exception;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;

/**
 * Room 등록/수정 요청이 가리키는 {@code RoomType} 이 존재하지만 다른 호텔 소속일 때 발생.
 * "리소스 없음(404)" 이 아닌 "요청 자원 간 소속 불일치(409)" 로 구분해 클라이언트가
 * 올바른 원인(= 다른 호텔의 RoomType 참조) 을 알 수 있게 한다.
 */
public class RoomTypeHotelMismatchException extends RuntimeException {

    private final HotelId requestedHotelId;
    private final RoomTypeId roomTypeId;
    private final HotelId actualHotelId;

    public RoomTypeHotelMismatchException(HotelId requestedHotelId,
                                          RoomTypeId roomTypeId,
                                          HotelId actualHotelId) {
        super("RoomType belongs to a different hotel: roomTypeId=" + roomTypeId.asString()
            + " requested=" + requestedHotelId.asString()
            + " actual=" + actualHotelId.asString());
        this.requestedHotelId = requestedHotelId;
        this.roomTypeId = roomTypeId;
        this.actualHotelId = actualHotelId;
    }

    public HotelId requestedHotelId() {
        return requestedHotelId;
    }

    public RoomTypeId roomTypeId() {
        return roomTypeId;
    }

    public HotelId actualHotelId() {
        return actualHotelId;
    }
}

package com.reservation.hotel.domain.exception;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeName;

/**
 * 같은 Hotel 내에 동일 이름의 RoomType 이 이미 존재할 때 발생. DB UNIQUE 제약의
 * 선제 방어 역할이며, race condition 으로 DB 레벨에서도 터질 경우 Infrastructure
 * 가 동일 exception 으로 변환해 재던진다.
 */
public class DuplicateRoomTypeNameException extends RuntimeException {

    private final HotelId hotelId;
    private final RoomTypeName name;

    public DuplicateRoomTypeNameException(HotelId hotelId, RoomTypeName name) {
        super("RoomType name already exists in hotel: hotelId=" + hotelId.asString()
            + " name=" + name.value());
        this.hotelId = hotelId;
        this.name = name;
    }

    public HotelId hotelId() {
        return hotelId;
    }

    public RoomTypeName name() {
        return name;
    }
}

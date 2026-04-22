package com.reservation.hotel.domain.exception;

import com.reservation.hotel.domain.model.Floor;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomNumber;

/**
 * 동일 Hotel + Floor + RoomNumber 조합이 이미 존재할 때 발생.
 */
public class DuplicateRoomNumberException extends RuntimeException {

    private final HotelId hotelId;
    private final Floor floor;
    private final RoomNumber number;

    public DuplicateRoomNumberException(HotelId hotelId, Floor floor, RoomNumber number) {
        super("Room already exists in hotel: hotelId=" + hotelId.asString()
            + " floor=" + floor.value() + " number=" + number.value());
        this.hotelId = hotelId;
        this.floor = floor;
        this.number = number;
    }

    public HotelId hotelId() {
        return hotelId;
    }

    public Floor floor() {
        return floor;
    }

    public RoomNumber number() {
        return number;
    }
}

package com.reservation.guest.domain.exception;

import com.reservation.guest.domain.model.GuestId;

/**
 * 요청된 {@code guestId} 로 Guest 를 찾을 수 없을 때. 공개 API 는 HTTP 404, gRPC 는
 * {@code Status.NOT_FOUND} 로 매핑된다.
 */
public class GuestNotFoundException extends RuntimeException {

    private final String guestId;

    public GuestNotFoundException(GuestId guestId) {
        super("Guest not found: id=" + guestId.asString());
        this.guestId = guestId.asString();
    }

    public String guestId() {
        return guestId;
    }
}

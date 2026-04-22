package com.reservation.hotel.domain.exception;

import com.reservation.hotel.domain.model.RoomId;
import com.reservation.hotel.domain.model.RoomStatus;

/**
 * Room 상태 전이 규칙을 위반하는 연산이 요청되었을 때 발생 (예: DEACTIVATED 에서
 * reassignRoomType, ACTIVE 가 아닌 상태에서 startMaintenance).
 *
 * <p>{@link IllegalStateException} 을 상위 핸들러가 catch 해 409 로 일괄 매핑하는 것은
 * 스프링 내부 예외까지 삼킬 수 있어 위험하다. 도메인 상태 전이 오류는 본 전용 타입으로
 * 분리해 Presentation 이 도메인 오류 코드로 안전하게 매핑한다.
 */
public class InvalidRoomStateTransitionException extends RuntimeException {

    private final RoomId roomId;
    private final RoomStatus currentStatus;
    private final String operation;

    public InvalidRoomStateTransitionException(RoomId roomId,
                                               RoomStatus currentStatus,
                                               String operation) {
        super(operation + " is not allowed in status " + currentStatus + " (roomId=" + roomId.asString() + ")");
        this.roomId = roomId;
        this.currentStatus = currentStatus;
        this.operation = operation;
    }

    public RoomId roomId() {
        return roomId;
    }

    public RoomStatus currentStatus() {
        return currentStatus;
    }

    public String operation() {
        return operation;
    }
}

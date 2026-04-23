package com.reservation.reservation.domain.exception;

/**
 * Inventory 불변식을 위반하는 연산이 시도됐을 때 던지는 도메인 예외.
 *
 * <p>주된 발생 경로:
 * <ul>
 *   <li>{@code totalRooms == 0} 상태에서 {@code removeRoom()} 호출
 *   <li>{@code availableRooms == 0} 상태에서 추가 차감 시도 (PR-2.2 이후)
 * </ul>
 *
 * <p>RuntimeException 으로 두어 Application Service 가 이벤트 consumer 맥락에서
 * warn 로그 + processed_events 기록 후 스킵할지 판단한다 — tombstone 정책상 중복
 * RoomDeleted 재전송은 허용 시나리오.
 */
public class InvalidInventoryOperationException extends RuntimeException {

    public InvalidInventoryOperationException(String message) {
        super(message);
    }
}

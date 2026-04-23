package com.reservation.reservation.domain.exception;

import com.reservation.reservation.domain.model.InventoryKey;

/**
 * 예약 취소 시 재고를 복원하려 했으나 결과 {@code availableRooms} 가 {@code totalRooms}
 * 를 초과하게 되어 차단된 경우 발생하는 도메인 예외.
 *
 * <p>{@link InvalidInventoryOperationException} (tombstone 정책 위반 — 무시 가능) 과 의미
 * 가 다르다. 본 예외는 **데이터 정합성 시그널** 로, 다음 중 하나를 의미한다:
 * <ul>
 *   <li>같은 예약에 대해 release 가 두 번 호출됨 (멱등성 가드 누락)</li>
 *   <li>예약 차감 후 hotel-events 에 의해 totalRooms 가 줄어들었으나 release 가 그대로
 *       시도됨 (이벤트 순서/처리 갭)</li>
 * </ul>
 *
 * <p>Application Service 는 본 예외를 흡수하지 말고 트랜잭션 롤백 → 상위 advice 가 500
 * 으로 응답하도록 두어야 한다 — 운영 대응이 필요한 시그널이기 때문 (ddd-architect M2).
 */
public class InventoryReleaseExceedsCapacityException extends RuntimeException {

    private final InventoryKey key;
    private final int totalRooms;
    private final int attemptedAvailableRooms;

    public InventoryReleaseExceedsCapacityException(InventoryKey key,
                                                     int totalRooms,
                                                     int attemptedAvailableRooms) {
        super("Release would exceed capacity for " + key
            + " (total=" + totalRooms + ", attempted available=" + attemptedAvailableRooms + ")");
        this.key = key;
        this.totalRooms = totalRooms;
        this.attemptedAvailableRooms = attemptedAvailableRooms;
    }

    public InventoryKey key() {
        return key;
    }

    public int totalRooms() {
        return totalRooms;
    }

    public int attemptedAvailableRooms() {
        return attemptedAvailableRooms;
    }
}

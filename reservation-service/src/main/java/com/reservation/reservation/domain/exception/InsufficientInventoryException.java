package com.reservation.reservation.domain.exception;

import com.reservation.reservation.domain.model.InventoryKey;

/**
 * 예약 생성 시 재고 차감을 시도했으나 {@code availableRooms == 0} 이어서 차감이
 * 불가능할 때 발생하는 도메인 예외.
 *
 * <p>발행처는 {@code RoomTypeInventory.decrease(Clock)} 단일 — Aggregate 가 자체
 * 불변식 위반을 즉시 예외로 드러낸다. Application Service 는 본 예외를 그대로 전파해
 * Presentation 계층에서 {@code 409 INSUFFICIENT_INVENTORY} 로 매핑한다 (PRD §8.1).
 *
 * <p>"hotel-events 가 아직 도착하지 않아 inventory row 자체가 없는" 케이스는 이 예외가
 * 아니라 Application Service 가 별도로 던지는 {@code InventoryNotInitializedException}
 * 으로 구분한다. 두 케이스는 클라이언트 응답 코드 (409 vs 404) 와 의미가 다르다.
 */
public class InsufficientInventoryException extends RuntimeException {

    private final InventoryKey key;

    public InsufficientInventoryException(InventoryKey key) {
        super("Insufficient inventory for " + key);
        this.key = key;
    }

    public InventoryKey key() {
        return key;
    }
}

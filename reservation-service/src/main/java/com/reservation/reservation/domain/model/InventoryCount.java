package com.reservation.reservation.domain.model;

/**
 * 재고 수량 VO — 음수 진입을 생성자에서 차단한다.
 *
 * <p>{@link RoomTypeInventory} 의 {@code totalRooms} · {@code availableRooms} 모두
 * 이 타입이므로 두 필드의 개별 비음수 불변식을 VO 한 곳에서 방어한다.
 * 두 값의 관계 불변식 ({@code available ≤ total}) 은 Aggregate 가 검증한다.
 *
 * <p>{@link #increment()} · {@link #decrement()} 는 새 인스턴스를 반환하는 pure function
 * 으로 설계해 Aggregate 가 setter 없이 내부 필드를 교체할 수 있게 한다.
 */
public record InventoryCount(int value) {

    public InventoryCount {
        if (value < 0) {
            throw new IllegalArgumentException("InventoryCount must be non-negative, was " + value);
        }
    }

    public static InventoryCount zero() {
        return new InventoryCount(0);
    }

    public static InventoryCount of(int value) {
        return new InventoryCount(value);
    }

    public InventoryCount increment() {
        return new InventoryCount(value + 1);
    }

    public InventoryCount decrement() {
        if (value == 0) {
            throw new IllegalStateException("Cannot decrement InventoryCount below zero");
        }
        return new InventoryCount(value - 1);
    }

    public boolean isZero() {
        return value == 0;
    }
}

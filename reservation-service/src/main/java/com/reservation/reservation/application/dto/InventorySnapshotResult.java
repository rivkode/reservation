package com.reservation.reservation.application.dto;

import com.reservation.reservation.domain.model.RoomTypeInventory;

import java.time.LocalDate;

/**
 * {@code StreamInventory} gRPC 의 한 요소. Application 경계에서 {@link RoomTypeInventory}
 * Aggregate 를 읽기 전용 DTO 로 납작하게 만든다. 상위 계층(gRPC server) 은 이 DTO 를 다시
 * {@code RoomTypeInventorySnapshot} proto 로 변환하여 스트리밍한다.
 *
 * <p>{@code totalInventory} 는 SoT 의 총 객실 수, {@code available} 은 현재 예약 가능
 * 객실 수 (오버부킹 정책 미도입 시점에서는 {@code availableRooms} 그대로 노출).
 */
public record InventorySnapshotResult(
    String hotelId,
    String roomTypeId,
    LocalDate stayDate,
    int totalInventory,
    int available
) {

    public static InventorySnapshotResult from(RoomTypeInventory inventory) {
        return new InventorySnapshotResult(
            inventory.hotelId().asString(),
            inventory.roomTypeId().asString(),
            inventory.stayDate(),
            inventory.totalRooms().value(),
            inventory.availableRooms().value()
        );
    }
}

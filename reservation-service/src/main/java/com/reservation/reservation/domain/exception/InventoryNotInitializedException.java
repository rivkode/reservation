package com.reservation.reservation.domain.exception;

import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.RoomTypeId;

import java.time.LocalDate;

/**
 * 예약 시도한 stayDate 에 {@code RoomTypeInventory} row 자체가 존재하지 않을 때 발생.
 *
 * <p>의미상 두 가지 경우:
 * <ol>
 *   <li>호텔/객실타입은 hotel-service 에 존재하나 hotel-events ({@code RoomCreated}) 가
 *       아직 reservation-service 에 도착하지 않음 — 일시적</li>
 *   <li>날짜가 horizon ({@code app.inventory.horizon-days}, 기본 90) 범위 밖 — 정책상
 *       너무 먼 미래 예약은 차단</li>
 * </ol>
 *
 * <p>두 경우 모두 클라이언트 입력에 대한 명확한 거부이므로 Presentation 계층에서
 * {@code 404 INVENTORY_NOT_INITIALIZED} 로 매핑한다 (계획 단계 사용자 승인).
 *
 * <p>{@link InsufficientInventoryException} 과는 의미가 다르다 — 본 예외는 row 자체가
 * 없음 (404), 후자는 row 는 있으나 available=0 (409 재고 부족).
 */
public class InventoryNotInitializedException extends RuntimeException {

    public InventoryNotInitializedException(HotelId hotelId, RoomTypeId roomTypeId, LocalDate date) {
        super("Inventory not initialized for hotel=" + hotelId.asString()
            + ", roomType=" + roomTypeId.asString() + ", date=" + date);
    }
}

package com.reservation.reservation.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * {@link RoomTypeInventory} 의 자연 복합 식별자.
 *
 * <p>{@code (hotelId, roomTypeId, stayDate)} 조합으로 날짜별 재고 집계 row 를 유일하게
 * 식별한다. Repository 조회/생성 파라미터로 반복해 쓰이므로 VO 로 묶어 타입 시그니처를
 * 단순화하고 호출자 실수(순서 바뀜)를 방지한다. {@code stayDate} 는 {@link LocalDate} 를
 * 그대로 쓴다 — 별도 검증 정책(과거 거부 등)이 없어 wrapper 가치가 낮다.
 */
public record InventoryKey(HotelId hotelId, RoomTypeId roomTypeId, LocalDate stayDate) {

    public InventoryKey {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        Objects.requireNonNull(stayDate, "stayDate");
    }
}

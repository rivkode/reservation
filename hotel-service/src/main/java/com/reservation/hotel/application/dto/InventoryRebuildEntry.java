package com.reservation.hotel.application.dto;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 캐시 재구축 시 Redis 에 upsert 할 한 건의 완전한 데이터. reservation-service 의
 * StreamInventory gRPC 응답 한 row 에 대응한다.
 */
public record InventoryRebuildEntry(HotelId hotelId,
                                    RoomTypeId roomTypeId,
                                    LocalDate date,
                                    int available,
                                    int total,
                                    Instant updatedAt) {

    public InventoryRebuildEntry {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (available < 0 || total < 0) {
            throw new IllegalArgumentException(
                "available/total must be non-negative: available=" + available + " total=" + total);
        }
    }
}

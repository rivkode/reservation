package com.reservation.reservation.domain.repository;

import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * RoomTypeInventory Aggregate 의 영속화 계약.
 *
 * <p>hotel-events 수신 처리는 "(hotelId, roomTypeId) 의 오늘~오늘+N일 범위 모든 row" 에
 * 대해 일괄 업데이트가 일반적이다. 날짜 범위 일괄 조회 ·
 * 저장 편의를 위해 {@link #findRange} · {@link #saveAll} 을 함께 노출한다.
 */
public interface RoomTypeInventoryRepository {

    /**
     * 단건 조회. PR-2.1 의 hotel-events 구독 경로는 {@link #findRange} 를 사용하며,
     * {@code findByKey} 는 PR-2.2 의 예약 생성 시 (hotelId, roomTypeId, checkInDate)
     * 단건 검증/차감 경로에서 사용한다.
     */
    Optional<RoomTypeInventory> findByKey(InventoryKey key);

    /**
     * {@code (hotelId, roomTypeId)} 의 {@code [fromDate, toDate]} 구간 (양끝 포함) row 를
     * {@code stayDate} 오름차순으로 반환. 구간 내 존재하지 않는 날짜는 건너뛴다.
     */
    List<RoomTypeInventory> findRange(HotelId hotelId,
                                       RoomTypeId roomTypeId,
                                       LocalDate fromDate,
                                       LocalDate toDate);

    void save(RoomTypeInventory inventory);

    void saveAll(List<RoomTypeInventory> inventories);
}

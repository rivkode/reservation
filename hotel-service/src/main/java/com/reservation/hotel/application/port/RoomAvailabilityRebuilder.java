package com.reservation.hotel.application.port;

import com.reservation.hotel.application.dto.InventoryRebuildEntry;

import java.util.List;

/**
 * 캐시 재구축(FR-H-08) 전용 쓰기 포트. PR-3.1 의 이벤트 기반 증감({@link RoomAvailabilityCache})
 * · PR-3.2 의 조회({@link RoomAvailabilityQuery}) 와 의도적으로 분리 — 재구축은 기존 key 존재
 * 여부와 무관하게 <strong>값 자체를 덮어쓴다</strong>. Redis HSET 의미론.
 *
 * <p>구현체는 Spring Data Redis pipeline 으로 묶어 한 번의 RTT 로 HSET 을 일괄 전송한다.
 * reservation-service SoT 에서 받아온 {@code available} / {@code total} / {@code updatedAt}
 * 을 그대로 Hash 에 반영하며, 부재였던 key 는 생성되고 기존 key 는 값이 교체된다.
 */
public interface RoomAvailabilityRebuilder {

    /**
     * 주어진 entry 리스트를 Redis Hash 로 upsert 한다. 입력이 비어있으면 아무 일도 하지 않음.
     */
    void upsertAll(List<InventoryRebuildEntry> entries);
}

package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.infrastructure.persistence.entity.HotelJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface HotelJpaRepository extends JpaRepository<HotelJpaEntity, UUID> {

    /**
     * 모든 호텔의 UUID 만 가져온다. 캐시 재구축에서 엔티티 전체를 load 할 필요가 없어
     * 별도 projection 쿼리로 처리한다. Aggregate 로드 비용(amenities ElementCollection 등)
     * 을 피하는 게 목적.
     */
    @Query("select h.id from HotelJpaEntity h")
    List<UUID> findAllIdsProjected();
}

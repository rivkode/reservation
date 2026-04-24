package com.reservation.hotel.domain.repository;

import com.reservation.hotel.domain.model.Hotel;
import com.reservation.hotel.domain.model.HotelId;

import java.util.List;
import java.util.Optional;

public interface HotelRepository {

    Hotel save(Hotel hotel);

    Optional<Hotel> findById(HotelId id);

    boolean existsById(HotelId id);

    /**
     * 가용성 캐시 재구축 배치(FR-H-08) 가 모든 호텔을 순회하기 위해 사용한다. 호텔 수가
     * 운영 규모로 커지면 페이징 또는 청크 조회로 전환해야 하지만, Phase 1 범위에선 단순
     * 전체 ID 목록이 가장 예측 가능.
     */
    List<HotelId> findAllIds();
}

package com.reservation.hotel.application.port;

import com.reservation.hotel.application.dto.RoomAvailabilitySnapshot;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;

import java.time.LocalDate;
import java.util.List;

/**
 * Redis {@code RoomAvailabilityView} 조회 포트. 쓰기(이벤트 구독 → 증감) 책임의
 * {@link RoomAvailabilityCache} 와 분리 — 두 포트 모두 같은 Redis 구현체에 의해 만족되지만,
 * Interface Segregation 원칙에 따라 호출자가 필요한 책임만 의존하도록 나눈다.
 *
 * <p>가용성 조회 API(FR-H-06) 는 본 포트만 사용한다. ADR 0004 의 "조회는 Redis 만" 정책에
 * 따라 reservation-service gRPC fallback 은 금지된다. value 부재 날짜는 결과에서 제외되며,
 * 호출자가 빈 응답 또는 부분 응답으로 해석한다.
 */
public interface RoomAvailabilityQuery {

    /**
     * 주어진 반개구간 {@code [fromInclusive, toExclusive)} 에 대해 각 날짜의 스냅샷을
     * 조회한다. 결과 리스트는 날짜 오름차순. 부재 key 는 결과에서 제외된다.
     */
    List<RoomAvailabilitySnapshot> readRange(HotelId hotelId,
                                             RoomTypeId roomTypeId,
                                             LocalDate fromInclusive,
                                             LocalDate toExclusive);
}

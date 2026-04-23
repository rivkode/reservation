package com.reservation.rate.domain.repository;

import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.RateId;
import com.reservation.rate.domain.model.RoomTypeId;
import com.reservation.rate.domain.model.RoomTypeRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@link RoomTypeRate} Aggregate 의 영속성 인터페이스. 도메인 계층이 소유하며
 * 구현은 {@code infrastructure/persistence/repository} 에 둔다.
 *
 * <p>조회 메서드를 자연키 기반으로도 노출하는 이유:
 * <ul>
 *   <li>등록(POST) 시 중복 검증을 Application Service 가 수행하기 위함</li>
 *   <li>범위 조회 API (FR-R-02) 가 자연키로만 의미를 갖기 때문</li>
 *   <li>{@link RateId} 는 surrogate 식별자이므로 외부가 보유할 일이 드묾</li>
 * </ul>
 */
public interface RoomTypeRateRepository {

    RoomTypeRate save(RoomTypeRate rate);

    Optional<RoomTypeRate> findById(RateId id);

    boolean existsByNaturalKey(HotelId hotelId, RoomTypeId roomTypeId, LocalDate date);

    /**
     * 범위 조회: {@code [from, to]} 포함 구간.
     *
     * @param from inclusive
     * @param to   inclusive, {@code from} 이상
     */
    List<RoomTypeRate> findByRange(HotelId hotelId, RoomTypeId roomTypeId, LocalDate from, LocalDate to);
}

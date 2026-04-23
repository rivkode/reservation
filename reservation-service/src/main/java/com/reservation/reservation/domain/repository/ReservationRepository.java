package com.reservation.reservation.domain.repository;

import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationId;

import java.util.Optional;

/**
 * Reservation Aggregate 의 영속화 계약.
 *
 * <p>PR-2.2 범위는 생성/조회 단건 위주. 예약 취소(상태 전이 저장) 는 PR-2.3 에서, 투숙객
 * /호텔별 목록 조회는 PR-2.4 에서 추가된다. Repository 도 그 시점에 함께 확장한다.
 */
public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(ReservationId id);
}

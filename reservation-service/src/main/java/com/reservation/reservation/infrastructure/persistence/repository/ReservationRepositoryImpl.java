package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationId;
import com.reservation.reservation.domain.repository.ReservationRepository;
import com.reservation.reservation.infrastructure.persistence.mapper.ReservationJpaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Reservation Aggregate 의 영속화 어댑터.
 *
 * <p>본 PR 의 호출자는 신규 생성 단일 케이스이므로 단순 {@code save → toDomain} 으로
 * 충분하다. 상태 전이(취소) 가 추가되는 PR-2.3 에서 {@code merge} 동작이 필요해질 때
 * Persistable 구현 또는 명시적 update 어댑터를 검토한다.
 */
@Repository
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        return ReservationJpaMapper.toDomain(
            jpaRepository.save(ReservationJpaMapper.toEntity(reservation)));
    }

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return jpaRepository.findById(id.value()).map(ReservationJpaMapper::toDomain);
    }
}

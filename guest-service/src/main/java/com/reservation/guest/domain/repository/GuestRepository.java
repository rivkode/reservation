package com.reservation.guest.domain.repository;

import com.reservation.guest.domain.model.Email;
import com.reservation.guest.domain.model.Guest;
import com.reservation.guest.domain.model.GuestId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link Guest} Aggregate 영속성 인터페이스. 도메인 계층 소유, 구현은
 * {@code infrastructure/persistence/repository} 에 둔다.
 *
 * <p>{@link #findAllByIds} 는 gRPC {@code BatchGetGuests} 에 대응하기 위해 제공한다 —
 * 입력 id 중 존재하는 것만 반환한다 (partial response 관례, ddd-architect M4 주석).
 */
public interface GuestRepository {

    Guest save(Guest guest);

    Optional<Guest> findById(GuestId id);

    List<Guest> findAllByIds(Collection<GuestId> ids);

    boolean existsByEmail(Email email);
}

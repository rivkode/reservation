package com.reservation.hotel.infrastructure.messaging;

import com.reservation.hotel.application.idempotency.ProcessedEventStore;
import com.reservation.hotel.infrastructure.persistence.entity.ProcessedEventJpaEntity;
import com.reservation.hotel.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link ProcessedEventStore} 의 JPA 구현 — hotel-service 로컬 {@code processed_events}
 * 테이블을 사용한다.
 *
 * <p>호출자({@code ReservationEventApplicationService}) 가 {@code @Transactional} 로
 * 감싸고 있으므로 본 클래스에는 별도 트랜잭션 애너테이션을 두지 않는다.
 *
 * <p>{@link #markProcessed} 는 {@code JpaRepository#save} (merge 기반) 가 아니라
 * {@link EntityManager#persist} 를 직접 호출한다. 이유: 두 consumer 스레드가 동시에 같은
 * {@code eventId} 를 처리하려 할 때 하나만 성공하고 다른 하나는 PK 충돌로 롤백되어야
 * 비즈니스 중복 처리가 방지된다. {@code save} 는 merge 로 동작해 두 트랜잭션 모두 성공한
 * 뒤 하나의 변경을 덮어쓰므로 멱등성 가드로는 부적절하다.
 *
 * <p>주의: Redis HINCRBY 는 MySQL 트랜잭션과 동기화되지 않는다. markProcessed 가 PK 충돌
 * 로 롤백돼도 이미 호출된 HINCRBY 는 되돌려지지 않는다 — 드물지만 발생 가능한 drift 는
 * PR-3.3 일일 재구축 배치가 보정한다.
 */
@Component
@RequiredArgsConstructor
public class JpaProcessedEventStore implements ProcessedEventStore {

    private final ProcessedEventJpaRepository repository;
    private final EntityManager entityManager;

    @Override
    public boolean isAlreadyProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void markProcessed(UUID eventId, String eventType, Instant processedAt) {
        entityManager.persist(new ProcessedEventJpaEntity(eventId, eventType, processedAt));
    }
}

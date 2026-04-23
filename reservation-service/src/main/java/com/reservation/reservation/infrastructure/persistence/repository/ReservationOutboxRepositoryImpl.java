package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.common.messaging.outbox.OutboxMessage;
import com.reservation.common.messaging.outbox.OutboxRepository;
import com.reservation.reservation.infrastructure.persistence.entity.ReservationOutboxJpaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * reservation-service 의 OutboxRepository 구현. common-infra 의 인터페이스에
 * spring-data-jpa 를 연결하는 얇은 어댑터.
 *
 * <p>{@link #save} 와 {@link #markPublished} 는 모두 상위 {@code @Transactional} 이
 * 반드시 존재해야 한다 ({@code Propagation.MANDATORY}). 본 PR 에서 호출자는
 * {@code CreateReservationApplicationService} 의 {@link org.springframework.transaction.support.TransactionTemplate}
 * 콜백 안. 트랜잭션이 없는 컨텍스트에서 호출되면 즉시
 * {@code IllegalTransactionStateException} 으로 드러나 잘못된 적재를 막는다.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ReservationOutboxRepositoryImpl implements OutboxRepository {

    private final ReservationOutboxJpaRepository jpaRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OutboxMessage message) {
        jpaRepository.save(new ReservationOutboxJpaEntity(
            message.id(),
            message.topic(),
            message.eventType(),
            message.eventId(),
            message.occurredAt(),
            message.partitionKey(),
            message.payload(),
            message.createdAt(),
            message.publishedAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxMessage> findUnpublished(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
        return jpaRepository.findUnpublished(PageRequest.of(0, limit)).stream()
            .map(ReservationOutboxRepositoryImpl::toMessage)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markPublished(UUID messageId, Instant publishedAt) {
        int updated = jpaRepository.markPublished(messageId, publishedAt);
        if (updated == 0) {
            log.debug("markPublished skipped (already published): id={}", messageId);
        }
    }

    private static OutboxMessage toMessage(ReservationOutboxJpaEntity entity) {
        return new OutboxMessage(
            entity.getId(),
            entity.getTopic(),
            entity.getEventType(),
            entity.getEventId(),
            entity.getOccurredAt(),
            entity.getPartitionKey(),
            entity.getPayload(),
            entity.getCreatedAt(),
            entity.getPublishedAt()
        );
    }
}

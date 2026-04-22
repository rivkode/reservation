package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.common.messaging.outbox.OutboxMessage;
import com.reservation.common.messaging.outbox.OutboxRepository;
import com.reservation.hotel.infrastructure.persistence.entity.HotelOutboxJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * hotel-service 의 OutboxRepository 구현. common-infra 의 인터페이스에 스프링 데이터
 * JPA 를 연결하는 얇은 어댑터.
 *
 * <p>{@link #save} 와 {@link #markPublished} 는 모두 상위 {@code @Transactional} 이
 * 반드시 존재해야 한다 ({@code Propagation.MANDATORY}). Outbox 적재는 Aggregate 저장과
 * 같은 로컬 트랜잭션에서 커밋되어야 하는 원자성 계약을 어기면 즉시
 * {@code IllegalTransactionStateException} 으로 실패해 개발 타임에 드러나게 한다.
 */
@Repository
public class HotelOutboxRepositoryImpl implements OutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(HotelOutboxRepositoryImpl.class);

    private final HotelOutboxJpaRepository jpaRepository;

    public HotelOutboxRepositoryImpl(HotelOutboxJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OutboxMessage message) {
        jpaRepository.save(new HotelOutboxJpaEntity(
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
            .map(HotelOutboxRepositoryImpl::toMessage)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markPublished(UUID messageId, Instant publishedAt) {
        // 0 row = 이미 발행 상태. 멱등 요구사항이므로 예외 없이 debug 로 흔적만 남긴다.
        int updated = jpaRepository.markPublished(messageId, publishedAt);
        if (updated == 0) {
            log.debug("markPublished skipped (already published): id={}", messageId);
        }
    }

    private static OutboxMessage toMessage(HotelOutboxJpaEntity entity) {
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

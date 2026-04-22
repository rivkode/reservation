package com.reservation.rate.infrastructure.persistence.repository;

import com.reservation.common.messaging.outbox.OutboxMessage;
import com.reservation.common.messaging.outbox.OutboxRepository;
import com.reservation.rate.infrastructure.persistence.entity.RateOutboxJpaEntity;
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
 * rate-service 의 OutboxRepository 구현. common-infra 의 인터페이스에 스프링 데이터
 * JPA 를 연결하는 얇은 어댑터 — hotel-service 의 HotelOutboxRepositoryImpl 과 동일 패턴.
 *
 * <p>{@link #save} 와 {@link #markPublished} 는 상위 {@code @Transactional} 존재가
 * 강제된다 ({@code Propagation.MANDATORY}). Outbox 적재가 비즈니스 상태 변경과 같은
 * 로컬 트랜잭션에서 커밋되어야 하는 원자성 계약을 개발 타임에 드러내기 위함.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class RateOutboxRepositoryImpl implements OutboxRepository {

    private final RateOutboxJpaRepository jpaRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OutboxMessage message) {
        jpaRepository.save(new RateOutboxJpaEntity(
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
            .map(RateOutboxRepositoryImpl::toMessage)
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

    private static OutboxMessage toMessage(RateOutboxJpaEntity entity) {
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

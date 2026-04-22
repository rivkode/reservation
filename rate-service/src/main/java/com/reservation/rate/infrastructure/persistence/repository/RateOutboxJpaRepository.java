package com.reservation.rate.infrastructure.persistence.repository;

import com.reservation.rate.infrastructure.persistence.entity.RateOutboxJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RateOutboxJpaRepository extends JpaRepository<RateOutboxJpaEntity, UUID> {

    /**
     * 아직 발행되지 않은 Outbox 메시지를 {@code created_at ASC} 순서로 조회.
     * {@code idx_rate_outbox_published_created (published_at, created_at)} 인덱스를 활용한다.
     *
     * <p>Phase 1 단일 인스턴스 가정이므로 단순 SELECT 를 사용한다. 멀티 인스턴스로
     * 전환할 때는 {@code SELECT ... FOR UPDATE SKIP LOCKED} 로 교체 필요.
     */
    @Query("""
        SELECT o FROM RateOutboxJpaEntity o
        WHERE o.publishedAt IS NULL
        ORDER BY o.createdAt ASC
        """)
    List<RateOutboxJpaEntity> findUnpublished(Pageable pageable);

    @Modifying
    @Query("""
        UPDATE RateOutboxJpaEntity o
        SET o.publishedAt = :publishedAt
        WHERE o.id = :id AND o.publishedAt IS NULL
        """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}

package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.reservation.infrastructure.persistence.entity.ReservationOutboxJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationOutboxJpaRepository extends JpaRepository<ReservationOutboxJpaEntity, UUID> {

    /**
     * 발행 대기중인 outbox 행을 {@code created_at ASC} 순서로 조회.
     * idx_reservation_outbox_published_created (published_at, created_at) 인덱스 활용.
     *
     * <p>Phase 1 단일 인스턴스 가정으로 단순 SELECT 사용. 멀티 인스턴스 전환 시
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} 로 교체 (OutboxRepository JavaDoc).
     */
    @Query("""
        SELECT o FROM ReservationOutboxJpaEntity o
        WHERE o.publishedAt IS NULL
        ORDER BY o.createdAt ASC
        """)
    List<ReservationOutboxJpaEntity> findUnpublished(Pageable pageable);

    @Modifying
    @Query("""
        UPDATE ReservationOutboxJpaEntity o
        SET o.publishedAt = :publishedAt
        WHERE o.id = :id AND o.publishedAt IS NULL
        """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}

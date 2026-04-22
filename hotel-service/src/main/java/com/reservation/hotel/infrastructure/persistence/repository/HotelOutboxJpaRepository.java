package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.hotel.infrastructure.persistence.entity.HotelOutboxJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HotelOutboxJpaRepository extends JpaRepository<HotelOutboxJpaEntity, UUID> {

    /**
     * 아직 발행되지 않은 Outbox 메시지를 {@code created_at ASC} 순서로 조회.
     * idx_hotel_outbox_published_created (published_at, created_at) 인덱스를 활용한다.
     *
     * <p>Phase 1 단일 인스턴스 가정이므로 단순 SELECT 를 사용한다. 멀티 인스턴스로
     * 전환할 때는 {@code SELECT ... FOR UPDATE SKIP LOCKED} 로 교체 필요
     * (OutboxRepository JavaDoc 참조).
     */
    @Query("""
        SELECT o FROM HotelOutboxJpaEntity o
        WHERE o.publishedAt IS NULL
        ORDER BY o.createdAt ASC
        """)
    List<HotelOutboxJpaEntity> findUnpublished(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE HotelOutboxJpaEntity o
        SET o.publishedAt = :publishedAt
        WHERE o.id = :id AND o.publishedAt IS NULL
        """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") java.time.Instant publishedAt);
}

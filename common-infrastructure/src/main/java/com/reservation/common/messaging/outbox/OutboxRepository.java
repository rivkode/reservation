package com.reservation.common.messaging.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 저장소 추상화. 각 서비스는 자체 {@code *_outbox} 테이블을 가지며
 * spring-data-jpa 로 본 interface 를 구현한다 (common-infra 는 서비스별 JpaEntity
 * 를 갖지 않기 위해 abstract 레이어만 제공).
 *
 * <p>구현체는 다음을 보장해야 한다:
 * <ul>
 *   <li>{@link #save} 는 호출자의 로컬 트랜잭션 안에서 실행되어야 한다.
 *       (Application Service 가 {@code @Transactional} 로 감싼다)</li>
 *   <li>{@link #findUnpublished} 는 동일 행이 동시에 두 relay 인스턴스에 의해 선택되지
 *       않도록 {@code FOR UPDATE SKIP LOCKED} 혹은 동등한 잠금 전략을 사용한다.
 *       Phase 1 에서는 단일 인스턴스 가정으로 단순 SELECT 로 시작해도 무방하지만
 *       멀티 인스턴스 도입 시 반드시 재검토한다.</li>
 *   <li>{@link #markPublished} 는 멱등 — 이미 markPublished 된 row 를 다시 호출해도
 *       예외 없이 무영향.</li>
 * </ul>
 */
public interface OutboxRepository {

    void save(OutboxMessage message);

    List<OutboxMessage> findUnpublished(int limit);

    void markPublished(UUID messageId, Instant publishedAt);
}

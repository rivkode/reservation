package com.reservation.hotel.application.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer 가 at-least-once 전달 환경에서 중복 처리를 막기 위한 멱등성 저장소 계약.
 *
 * <p>Application Service 가 이벤트 처리 트랜잭션의 <strong>앞</strong> 에서
 * {@link #isAlreadyProcessed} 로 체크하고, 처리가 끝나면 같은 트랜잭션 내에서
 * {@link #markProcessed} 로 기록한다. 동일 트랜잭션에 묶여야 비즈니스 변경(Redis 증감)
 * 과 "처리 완료" 기록이 원자적으로 커밋되어 부분 실패가 남지 않는다.
 *
 * <p>Redis 는 별도 connection 이라 MySQL 트랜잭션으로 롤백되지 않는다. 그러므로 본 PR
 * 에선 Redis 호출을 MySQL {@code processed_events} insert 와 같은 Application Service
 * 메서드 내에서 순서대로 수행하고, insert 가 PK 충돌로 실패하면 이미 처리된 이벤트로
 * 간주한다. "Redis 성공 + markProcessed 실패" 로 중복 증감이 일어나는 최악 시나리오는
 * PR-3.3 일일 재구축 배치가 보정한다.
 *
 * <p>저장소 범위는 consumer 그룹(= 서비스) 단위다. hotel-service 가 reservation-events
 * 외 다른 토픽을 함께 구독하게 되어도 {@code eventId} 가 전역 UUID 이므로 테이블 하나로
 * 충분하다. reservation-service 의 동일 이름 인터페이스와 의도적으로 중복 — 서비스간
 * 도메인 공유 금지 원칙에 따라 각 서비스 내부에 복제해 둔다.
 */
public interface ProcessedEventStore {

    boolean isAlreadyProcessed(UUID eventId);

    void markProcessed(UUID eventId, String eventType, Instant processedAt);
}

package com.reservation.reservation.application.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer 가 at-least-once 전달 환경에서 중복 처리를 막기 위한 멱등성 저장소 계약.
 *
 * <p>Application Service 가 이벤트 처리 트랜잭션의 **앞** 에서 {@link #isAlreadyProcessed}
 * 로 체크하고, 처리가 끝나면 같은 트랜잭션 내에서 {@link #markProcessed} 로 기록한다.
 * 동일 트랜잭션에 묶여야 비즈니스 변경과 "처리 완료" 기록이 원자적으로 커밋되어 부분
 * 실패가 남지 않는다.
 *
 * <p>저장소 범위는 consumer 그룹(= 서비스) 단위다. reservation-service 가 hotel-events 와
 * 향후 다른 토픽을 함께 구독하게 되어도 {@code eventId} 가 전역 UUID 이므로 테이블 하나로
 * 충분하다. consumer 그룹 구분이 필요해지면 구현체에서 컬럼을 추가한다.
 *
 * <p>본 PR 에선 reservation-service 로컬 JPA 구현으로 시작한다. 다른 서비스에서도 Kafka
 * 구독이 생기면 common-infrastructure 로 승격한다 (Q3=A 결정).
 */
public interface ProcessedEventStore {

    boolean isAlreadyProcessed(UUID eventId);

    void markProcessed(UUID eventId, String eventType, Instant processedAt);
}

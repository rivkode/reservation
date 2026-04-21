package com.reservation.contracts.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 서비스간 Kafka 이벤트 공통 계약.
 *
 * <p>모든 이벤트 record 는 고유 {@code eventId} 와 발행 시각 {@code occurredAt} 을
 * 반드시 포함한다 (null 금지; 각 record 의 compact constructor 에서 강제). 구독자는
 * 수신한 {@code eventId} 를 각 서비스의 MySQL {@code processed_events} 테이블에
 * 기록해 at-least-once 전달 환경에서 멱등성을 보장한다 (PRD §6 · §9).
 *
 * <p>이 인터페이스는 contracts 모듈에만 존재하며, 서비스 도메인 계층의
 * 도메인 이벤트(내부용)와는 별개다. 구체적인 직렬화 포맷(JSON + JavaTimeModule
 * vs. Avro 등) 및 토픽 내 다형성 처리 방식은 PR-0.3 의 common-infrastructure
 * 설계 ADR 에서 확정된다.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();
}

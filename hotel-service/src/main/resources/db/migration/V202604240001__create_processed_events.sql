-- PR-3.1 hotel-service: reservation-events 구독 멱등성 가드용 테이블.
-- 대상: FR-H-07 (reservation-events 구독 → Redis 캐시 갱신), ADR 0004 캐시 동기화 §절.
--
-- reservation-service 의 동일 이름 테이블과 의도적으로 중복 정의한다 — Database per
-- Service 원칙에 따라 각 서비스의 consumer 가 자기 DB 에만 기록한다. 스키마는
-- reservation-service/V202604230001 와 일치시켜 향후 common-infrastructure 로 승격할
-- 때 마이그레이션 없이 교체 가능하도록 한다.

CREATE TABLE processed_events (
    event_id     BINARY(16)   NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_processed_events_type_time ON processed_events (event_type, processed_at);

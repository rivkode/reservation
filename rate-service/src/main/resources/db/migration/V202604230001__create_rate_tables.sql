-- Phase 1 PR-1.2: rate-service 요금 정책 + Outbox 초기 스키마
-- 대상: FR-R-01 · FR-R-02 · FR-R-03

-- 1. RoomTypeRate (Aggregate Root)
--    surrogate PK id(BINARY(16)) + 자연키 UNIQUE(hotel_id, room_type_id, rate_date).
--    hotel_id · room_type_id 는 hotel-service 가 발급한 UUID 참조 (foreign ID).
--    amount 는 통화 최소 단위 정수 (예: KRW 원 단위). currency 는 ISO-4217 3글자.
CREATE TABLE room_type_rate (
    id            BINARY(16)  NOT NULL,
    hotel_id      BINARY(16)  NOT NULL,
    room_type_id  BINARY(16)  NOT NULL,
    rate_date     DATE        NOT NULL,
    amount        BIGINT      NOT NULL,
    currency      VARCHAR(3)  NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_room_type_rate_natural UNIQUE (hotel_id, room_type_id, rate_date),
    CONSTRAINT chk_room_type_rate_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_room_type_rate_range ON room_type_rate (hotel_id, room_type_id, rate_date);

-- 2. Outbox 테이블 (common-infra OutboxRepository 구현이 사용)
--    hotel-service 의 hotel_outbox 와 동일 구조 · 동일 인덱스.
CREATE TABLE rate_outbox (
    id            BINARY(16)   NOT NULL,
    topic         VARCHAR(128) NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    event_id      BINARY(16)   NOT NULL,
    occurred_at   DATETIME(6)  NOT NULL,
    partition_key VARCHAR(128) NOT NULL,
    payload       LONGBLOB     NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    published_at  DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rate_outbox_published_created ON rate_outbox (published_at, created_at);

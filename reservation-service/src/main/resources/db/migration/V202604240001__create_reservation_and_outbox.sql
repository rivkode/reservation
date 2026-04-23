-- PR-2.2 reservation-service: Reservation Aggregate + Outbox 테이블
-- 대상: FR-RSV-02 (예약 생성 API + ReservationCreatedEvent 발행)
-- 정합: PRD §3.2 SoT, ADR 0003 Saga, ddd-architect H1 (BillingQuote 스냅샷)

-- 1. Reservation Aggregate Root.
--    UUID v7 PK 의 시간순 정렬 성질로 클러스터드 인덱스 삽입 단편화를 최소화.
--    total_amount/currency/quoted_at 은 BillingQuote VO 의 직렬화 — rate 변경에도 불변.
CREATE TABLE reservation (
    id                BINARY(16)   NOT NULL,
    hotel_id          BINARY(16)   NOT NULL,
    room_type_id      BINARY(16)   NOT NULL,
    guest_id          BINARY(16)   NOT NULL,
    check_in_date     DATE         NOT NULL,
    check_out_date    DATE         NOT NULL,
    number_of_guests  INT          NOT NULL,
    total_amount      BIGINT       NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    quoted_at         DATETIME(6)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_reservation_period      CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_reservation_guests      CHECK (number_of_guests >= 1),
    CONSTRAINT chk_reservation_amount      CHECK (total_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PR-2.4 의 투숙객/호텔별 조회 인덱스는 그 PR 에서 추가한다 — 본 PR 은 단건 조회만.

-- 2. Outbox 테이블 (common-infra OutboxRepository 구현이 사용).
--    findUnpublished 용 인덱스: published_at IS NULL 행을 빠르게 조회.
CREATE TABLE reservation_outbox (
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

CREATE INDEX idx_reservation_outbox_published_created
    ON reservation_outbox (published_at, created_at);

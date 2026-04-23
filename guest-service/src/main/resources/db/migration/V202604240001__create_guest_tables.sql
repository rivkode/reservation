-- Phase 1 PR-1.3: guest-service 투숙객 초기 스키마
-- 대상: FR-G-01 등록 · FR-G-02 조회 · FR-G-03 변경
-- 본 PR 은 이벤트 발행이 없어 Outbox 테이블은 포함하지 않는다 (PRD §7.2).

-- Guest (Aggregate Root)
--   surrogate PK id (BINARY(16) UUIDv7)
--   email 은 Aggregate 집합 불변식(유일성) 최종 방어선 — UNIQUE 로 DB 가 보증
--   phone_number 는 Domain VO 가 E.164 (\"+\" + 5~15 digits) 로 정규화한 값만 저장
CREATE TABLE guest (
    id            BINARY(16)   NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    phone_number  VARCHAR(16)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_guest_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

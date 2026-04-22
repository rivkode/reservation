-- Phase 1 PR-1.1b: hotel-service 마스터 데이터 + Outbox 초기 스키마
-- 대상: FR-H-01 ~ FR-H-05

-- 1. Hotel (마스터 데이터)
CREATE TABLE hotel (
    id              BINARY(16)   NOT NULL,
    name            VARCHAR(200) NOT NULL,
    address_street  VARCHAR(200) NOT NULL,
    address_city    VARCHAR(100) NOT NULL,
    address_country VARCHAR(100) NOT NULL,
    star_rating     INT          NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_hotel_city ON hotel (address_city);
CREATE INDEX idx_hotel_country ON hotel (address_country);

-- 2. Hotel amenity (Hotel AR 내부 Set<Amenity> 의 저장)
CREATE TABLE hotel_amenity (
    hotel_id BINARY(16)  NOT NULL,
    amenity  VARCHAR(32) NOT NULL,
    PRIMARY KEY (hotel_id, amenity),
    CONSTRAINT fk_hotel_amenity_hotel FOREIGN KEY (hotel_id) REFERENCES hotel (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. RoomType (별도 Aggregate. hotelId 참조)
--    UNIQUE(hotel_id, name) 으로 애플리케이션 race condition 방어.
CREATE TABLE room_type (
    id            BINARY(16)   NOT NULL,
    hotel_id      BINARY(16)   NOT NULL,
    name          VARCHAR(100) NOT NULL,
    max_occupancy INT          NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_room_type_hotel_name UNIQUE (hotel_id, name),
    CONSTRAINT fk_room_type_hotel FOREIGN KEY (hotel_id) REFERENCES hotel (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_room_type_hotel ON room_type (hotel_id);

-- 4. Room (별도 Aggregate. soft-delete 를 위해 status enum 사용 — DEACTIVATED 도 row 보존).
--    UNIQUE(hotel_id, floor_no, room_number) 로 물리 객실 중복 등록 방지.
CREATE TABLE room (
    id            BINARY(16)  NOT NULL,
    hotel_id      BINARY(16)  NOT NULL,
    room_type_id  BINARY(16)  NOT NULL,
    floor_no      INT         NOT NULL,
    room_number   VARCHAR(16) NOT NULL,
    status        VARCHAR(32) NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_room_hotel_floor_number UNIQUE (hotel_id, floor_no, room_number),
    CONSTRAINT fk_room_hotel FOREIGN KEY (hotel_id) REFERENCES hotel (id),
    CONSTRAINT fk_room_type FOREIGN KEY (room_type_id) REFERENCES room_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_room_hotel ON room (hotel_id);
CREATE INDEX idx_room_room_type ON room (room_type_id);

-- 5. Outbox 테이블 (common-infra OutboxRepository 구현이 사용)
--    findUnpublished 용 인덱스: published_at IS NULL 을 빠르게 조회.
CREATE TABLE hotel_outbox (
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

CREATE INDEX idx_hotel_outbox_published_created ON hotel_outbox (published_at, created_at);

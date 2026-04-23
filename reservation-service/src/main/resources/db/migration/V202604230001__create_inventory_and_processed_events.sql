-- PR-2.1 reservation-service: 재고 SoT + hotel-events 구독용 초기 스키마
-- 대상: FR-RSV-04 (hotel-events 구독 → RoomTypeInventory 레코드 추가/제거)
--      PRD §3.2 (재고 소유 원칙), §9.2 (호텔 정보 동기화)

-- 1. RoomTypeInventory — 자연 복합 키 (hotel_id, room_type_id, stay_date) 로 식별.
--    PRD §13 Q7 의 "90일 범위" 와 정합. tombstone 정책상 total=0 row 는 보존한다.
CREATE TABLE room_type_inventory (
    hotel_id        BINARY(16)  NOT NULL,
    room_type_id    BINARY(16)  NOT NULL,
    stay_date       DATE        NOT NULL,
    total_rooms     INT         NOT NULL,
    available_rooms INT         NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (hotel_id, room_type_id, stay_date),
    CONSTRAINT chk_inventory_total_non_negative     CHECK (total_rooms     >= 0),
    CONSTRAINT chk_inventory_available_non_negative CHECK (available_rooms >= 0),
    CONSTRAINT chk_inventory_available_le_total     CHECK (available_rooms <= total_rooms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 가용성 조회 (hotel-service 캐시 재구축용 StreamInventory — PR-2.4) 를 위한 보조 인덱스.
CREATE INDEX idx_inventory_stay_date ON room_type_inventory (stay_date);

-- 2. RoomAssignment — Room 하나가 현재 어느 RoomType 에 속하는지의 로컬 매핑.
--    contracts 의 RoomUpdatedEvent 에는 이전 roomTypeId 가 없어 reservation-service 가
--    자체적으로 "현재 매핑" 을 보유해 이벤트 수신 시 비교한다.
CREATE TABLE room_assignment (
    room_id      BINARY(16)  NOT NULL,
    hotel_id     BINARY(16)  NOT NULL,
    room_type_id BINARY(16)  NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_room_assignment_hotel      ON room_assignment (hotel_id);
CREATE INDEX idx_room_assignment_room_type  ON room_assignment (room_type_id);

-- 3. processed_events — Kafka consumer 멱등성 가드.
--    (Q3=A) reservation-service 로컬 구현으로 시작, 다른 서비스에도 consumer 가
--    생기면 common-infrastructure 로 승격한다. 현재 범위는 consumer 그룹 = 서비스
--    하나이므로 event_id 단일 PK 로 충분.
CREATE TABLE processed_events (
    event_id     BINARY(16)   NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    processed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_processed_events_type_time ON processed_events (event_type, processed_at);

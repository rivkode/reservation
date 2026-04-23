-- PR-2.3 reservation-service: Reservation 의 취소 상세 컬럼 추가.
-- 대상: FR-RSV-03 (예약 취소 + Saga 보상 트랜잭션). status 전이만으론 어떤 정책으로
-- 어느 시점에 어떤 사유로 취소됐는지 알 수 없으므로 4 컬럼 평탄 매핑한다.
-- 정합: ddd-architect C1 (Cancellation VO 묶음, dead-data 우려 약화 위해 policy_name 보존)

ALTER TABLE reservation
    ADD COLUMN cancelled_at              DATETIME(6)  NULL AFTER status,
    ADD COLUMN cancellation_reason       VARCHAR(32)  NULL AFTER cancelled_at,
    ADD COLUMN refund_rate               DECIMAL(3,2) NULL AFTER cancellation_reason,
    ADD COLUMN cancellation_policy_name  VARCHAR(64)  NULL AFTER refund_rate;

-- Aggregate 불변식 (status==CANCELLED) ↔ (cancellation!=null) 의 DB 수준 가드.
-- 4 컬럼 모두 함께 채워지거나 모두 NULL 이어야 함.
ALTER TABLE reservation
    ADD CONSTRAINT chk_reservation_cancellation_consistency CHECK (
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND refund_rate IS NOT NULL
            AND cancellation_policy_name IS NOT NULL)
        OR
        (status <> 'CANCELLED'
            AND cancelled_at IS NULL
            AND cancellation_reason IS NULL
            AND refund_rate IS NULL
            AND cancellation_policy_name IS NULL)
    );

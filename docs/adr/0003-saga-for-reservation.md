# ADR 0003 — Reservation Saga (보상 트랜잭션 기반 분산 일관성)

- **상태**: Accepted
- **작성일**: 2026-04-22
- **관련 PR**: PR-docs-2 · PR-2.2 reservation-service 예약 생성 (예정)

## 맥락

예약 생성은 다음을 수반한다.
1. reservation-service: `RoomTypeInventory` 차감 + `Reservation` 생성
2. rate-service: `Billing` 생성
3. guest-service: 방문 이력 갱신
4. hotel-service: Redis `RoomAvailabilityView` 감소

Database per Service 원칙상 분산 트랜잭션(2PC) 배제.

## 결정

**Choreographed Saga + Outbox 패턴 + 보상 트랜잭션** 으로 최종 일관성을 확보한다.

### 정상 경로

1. **reservation-service 로컬 트랜잭션**:
   - `RoomTypeInventory` 차감 + `Reservation` 생성 + `outbox` 에 `ReservationCreated` 적재
   - `Reservation` 과 N개의 `RoomTypeInventory` 가 **별개 Aggregate 임에도 같은 트랜잭션
     에서 변경**된다. 일반적으로 다중 Aggregate update 는 권장되지 않으나, 본 도메인은
     "예약 행위 = 재고 차감 + 예약 레코드 생성" 이 atomic 해야 오버부킹을 방지할 수 있어
     (PRD §3.2 SoT 요구사항) 같은 로컬 트랜잭션이 불가피하다. Aggregate 경계는 생명주기 ·
     일관성 경계 분리(예약 단건 vs 날짜별 재고 집계) 로 정당화되며, 트랜잭션 경계와 분리해
     설계한다 — 트랜잭션은 인프라 결정이고 Aggregate 는 도메인 결정이다.
   - 외부 IO(guest-service · rate-service gRPC 호출) 는 트랜잭션 **밖** 에서 수행해
     커넥션 점유 시간을 최소화한다.
2. Outbox publisher 가 `reservation-events` 토픽으로 발행
3. 구독자 처리:
   - **rate-service**: Billing 생성 → `billing-events/BillingCreated` 발행
   - **hotel-service**: Redis `RoomAvailabilityView` 감소 (멱등)
   - **guest-service**: 방문 이력 갱신 (멱등)
4. reservation-service 가 `BillingCreated` 수신 → `Reservation` 을 확정 단계로 전이

### 보상 경로 (Billing 실패)

- rate-service Billing 생성 실패 → `billing-events/BillingCreationFailed` 발행
- reservation-service 수신 → 로컬 트랜잭션: **예약 취소 + 재고 복원** + `outbox` 에 `ReservationCancelled` 적재
- 다른 구독자들이 `ReservationCancelled` 수신으로 각자 상태 원복

### 취소 경로 (사용자 요청)

- reservation-service 로컬 트랜잭션: `Reservation` 상태 전이 + Inventory 복원 + `outbox/ReservationCancelled` 적재
- 24시간 기준 위약금 정책 적용 (PRD §5 FR-RSV-03, Plan Q3)

## 근거

- **강제 2PC 회피**: XA · 외부 Saga 프레임워크(Temporal 등) 는 초기 단계 운영 복잡도 과잉
- **Outbox = Producer at-least-once 일관성**: 로컬 DB 트랜잭션 성공 = 이벤트 발행 보장
- **Consumer 멱등성**: `processed_events` 테이블 + `eventId` 로 중복 수신 안전
- **Choreographed (orchestrator 없음)**: 각 서비스가 이벤트를 독립 처리. 신규 구독자 추가 시 reservation-service 변경 불필요 → 확장 용이

## 대안 (기각)

- **Orchestrated Saga** (별도 조정자 서비스): 단계가 훨씬 많아질 때 이점 있으나 현 4단계 구조에서 오버헤드 큼
- **2PC / XA**: MySQL · Kafka · Redis 혼합 환경에서 실용적 XA 지원 제한
- **낙관적 병합 후 수동 조정**: 오버부킹 리스크가 사업 요구(§3 오버부킹 방지) 위반

## 운영 정책

- Outbox publisher 는 **별도 스케줄러 / batch** (Debezium 또는 커스텀 poll) 로 `outbox.published_at IS NULL` row 를 Kafka 전송 후 `published_at` 업데이트
- 재시도 실패 3회 → DLQ 이관 + 알림
- 이벤트 스키마 변경은 **backward compatible** 만 허용 (필드 추가 O · 제거/타입 변경 X)

## 결과

- `reservation-service` 에 `outbox` 테이블 추가 (PR-2.2)
- 각 구독 서비스에 `processed_events` 테이블 추가 (PR-2.x / PR-3.x)
- Saga 흐름 검증은 Testcontainers (MySQL + Kafka) 통합 테스트 (PR-4.2)

## 참조

- PRD §9 데이터 일관성 전략 (Saga)
- ADR 0001 DomainEvent · ADR 0002 gRPC/Kafka · ADR 0004 CQRS
- `.claude/skills/module-boundary/references/saga-example.md`

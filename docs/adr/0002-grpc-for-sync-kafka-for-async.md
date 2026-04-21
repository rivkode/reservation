# ADR 0002 — 동기 통신 gRPC · 비동기 통신 Kafka

- **상태**: Accepted
- **작성일**: 2026-04-22
- **관련 PR**: PR-0.2 contracts · PR-0.3 common-infrastructure · PR-docs-2

## 맥락

MSA 서비스 4종 (hotel / rate / guest / reservation) 간 데이터 교환이 불가피. 기본 선택지: (A) REST/HTTP · (B) gRPC · (C) Kafka · (D) 공유 DB. (D) 는 Database per Service 원칙으로 배제 (PRD §3).

## 결정

- **동기 · 조회 성격**: **gRPC** 사용
  - `guest.proto` — `GetGuest` · `BatchGetGuests` (reservation 이 예약 생성 시 투숙객 검증)
  - `rate.proto` — `GetRoomTypeRate` (reservation 이 예약 생성 시 요금 견적)
  - `reservation.proto` — `StreamInventory` (hotel 이 Redis 캐시 재구축 배치)
  - `hotel.proto` — `GetHotel` · `GetRoomType` (향후 다른 서비스 조회용)

- **상태 전파 · 최종 일관성**: **Kafka** 사용
  - `hotel-events` (`RoomCreated` · `RoomUpdated` · `RoomDeleted`)
  - `rate-events` (`RoomTypeRateChanged`)
  - `reservation-events` (`ReservationCreated` · `ReservationCancelled`)
  - `billing-events` (`BillingCreated` · `BillingCreationFailed`)

## 근거

| 기준 | gRPC | Kafka | REST |
|---|---|---|---|
| 타입 안전 (schema-first) | ★★★ (proto) | ★★ (record + 헤더) | ★ |
| 낮은 지연 조회 | ★★★ | ★ (queue 특성) | ★★ |
| 이벤트 팬아웃 · 상태 전파 | ★ | ★★★ | ★ |
| backpressure · retention | ★ | ★★★ | ★ |
| Saga 보상 트랜잭션 | ★ | ★★★ | ★ |

동기 호출은 **낮은 지연 · 타입 안전** 이 중요한 조회 성격에 한정하고, 상태 전파와 Saga 흐름은 비동기 이벤트가 독립 실행 · 최종 일관성에 부합.

## 제약 · 정책

- 모든 gRPC 클라이언트 호출은 **Deadline 3초** + **Resilience4j Circuit Breaker · Retry** 필수 (PRD §6, CLAUDE.md 원칙 #8)
- 모든 Kafka 구독자는 **`processed_events` 테이블** 로 멱등성 강제 (ADR 0001)
- Producer 는 **Outbox 패턴** 으로 로컬 트랜잭션 일관성 확보 (ADR 0003 상세)
- 공유는 `contracts` 모듈 외 금지 (ArchUnit `noCrossServiceImports` 로 강제)

## 대안 (기각)

- **REST/HTTP 만**: 타입 안전성 · streaming 지원 약함, 이벤트 팬아웃이 각 구독자 개별 엔드포인트 필요 → 결합도 상승.
- **전부 Kafka**: 예약 생성 시 guest 검증을 이벤트로 처리하면 사용자 대기 분 단위 지연 허용 필요 → PRD §6 p99 < 1s 위반.
- **REST + Kafka 혼용**: 가능하나 gRPC 가 schema-first · streaming · 코드 생성 측면에서 우위.

## 결과

- `contracts` 모듈에 `.proto` 와 이벤트 record 가 공존 (PR-0.2 에서 구축 완료)
- `common-infrastructure` 에 gRPC 클라이언트 인터셉터 (Deadline · Circuit Breaker) 와 Kafka Outbox publisher · Consumer 역직렬화 유틸 도입 예정 (PR-2.x)
- Phase 1 에서 서비스별로 REST (외부 클라이언트) + gRPC server (서비스간) + Kafka producer/consumer (이벤트) 3가지 진입점

## 참조

- PRD §7 · §6
- CLAUDE.md 원칙 #1 · #8
- ADR 0001 DomainEvent 직렬화 · ADR 0003 Saga · ADR 0004 CQRS
- `.claude/skills/module-boundary/SKILL.md` §3.5 · §4

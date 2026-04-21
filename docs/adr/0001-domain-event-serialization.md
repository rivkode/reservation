# ADR 0001 — DomainEvent 직렬화 · 토픽 내 다형성 전략

- **상태**: Accepted
- **작성일**: 2026-04-21
- **관련 PR**: #2 (PR-0.2 contracts 모듈), #3 (PR-0.3 common-infrastructure)
- **관련 스킬**: `.claude/skills/module-boundary/` §4 (Kafka 이벤트)

## 맥락 (Context)

`contracts` 모듈의 `DomainEvent` 는 8종 이벤트 record 가 구현하는 공통 계약이다. PRD §7.2 에 따라 Kafka 토픽은 **타입별이 아닌 도메인별**로 묶여 있어 한 토픽이 여러 이벤트 타입을 담는다:

| 토픽 | 이벤트 타입 |
|---|---|
| `hotel-events` | `RoomCreatedEvent` · `RoomUpdatedEvent` · `RoomDeletedEvent` |
| `rate-events` | `RoomTypeRateChangedEvent` |
| `reservation-events` | `ReservationCreatedEvent` · `ReservationCancelledEvent` |
| `billing-events` | `BillingCreatedEvent` · `BillingCreationFailedEvent` |

**Producer** 는 구체 record 를 알고 있어 직렬화에 문제가 없다.
**Consumer** 는 바이트 스트림만 받으므로 어떤 record 로 역직렬화할지 결정해야 한다. `DomainEvent` 인터페이스만으로는 구체 타입 식별이 불가능하다.

## 결정 (Decision)

### 1. 다형성 식별 — Kafka record **헤더** 에 `event-type` 기록

Outbox publisher 가 발행 시 Kafka `ProducerRecord` 의 헤더에 다음 3건을 채운다:

| 헤더 키 | 값 | 용도 |
|---|---|---|
| `event-type` | 이벤트 record 의 `SimpleName` (예: `RoomCreatedEvent`) | Consumer 의 역직렬화 타입 선택 |
| `event-id` | `DomainEvent.eventId()` UUID 문자열 | 멱등성 검증 (`processed_events` 테이블) |
| `occurred-at` | `DomainEvent.occurredAt()` ISO-8601 Instant | 지연 관찰·디버깅 |

Payload 본문은 **JSON + Jackson JavaTimeModule** (고유 타입 메타 없음).

### 2. Consumer 측 역직렬화

각 서비스의 `infrastructure/messaging/` 하위에 공용 역직렬화기가 위치한다:

```
Map<String, Class<? extends DomainEvent>> registry = Map.of(
    "RoomCreatedEvent", RoomCreatedEvent.class,
    "RoomUpdatedEvent", RoomUpdatedEvent.class,
    ...
);
```

- 헤더 `event-type` 조회 → registry 에서 `Class` 획득 → Jackson `ObjectMapper.readValue(bytes, class)`
- **미등록 타입은 WARN 로그 + DLQ 전송 후 커밋** (새 이벤트가 구독자 측에 미배포되어도 기존 서비스 다운 방지).
- **역직렬화 실패**(payload 스키마 불일치 등) 는 예외를 DLQ 로 이관하며 offset 을 커밋한다.

### 3. 멱등성

각 서비스 MySQL 스키마에 `processed_events` 테이블을 둔다.

```sql
CREATE TABLE processed_events (
    event_id BINARY(16) PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    processed_at DATETIME(6) NOT NULL
);
```

Consumer 는 비즈니스 처리 **같은 트랜잭션** 안에서 `INSERT IGNORE` (또는 `ON DUPLICATE KEY UPDATE`) 로 `event_id` 를 기록한다. 이미 존재하면 스킵.

**변환 경계**: Kafka `event-id` 헤더는 **문자열 UUID** (ISO 8601 표현), MySQL 저장은 **BINARY(16)** 으로 통일한다. 변환은 common-infrastructure 에 도입할 `UuidBinaryConverter` 유틸 (PR-3.1 시점 추가 예정) 에서 `UUID.fromString(...)` → `ByteBuffer.putLong(msb).putLong(lsb)` 방식으로 수행하며, 각 서비스의 `ProcessedEventJpaRepository` 는 이 유틸만 사용한다. 이 결정은 단일 변환 지점을 보장해 토픽별/서비스별 스키마가 갈라지지 않게 한다.

### 4. `DomainEvent` 인터페이스 유지 — sealed 로 바꾸지 않음

- **왜**: 외부 팀이 신규 이벤트를 추가할 때 contracts 모듈을 건드리지 않고 구독만 추가할 수 있어야 한다 (확장 개방).
- **대안 (기각)**: `sealed` 인터페이스 + `permits` 목록. 컴파일 타임 안전하나 확장 시 contracts 수정 강제.

## 대안 (Alternatives considered)

| 방안 | 장점 | 단점 | 판정 |
|---|---|---|---|
| Jackson `@JsonTypeInfo` + `@JsonSubTypes` in payload | 단일 payload | contracts 에 모든 서브타입 선언. sealed 와 유사한 결합 | 기각 |
| 타입별 1 토픽 | 단순 | 토픽 수 폭증, 동일 Aggregate 이벤트의 순서 보장 어려움 | 기각 |
| Avro / Confluent Schema Registry | 스키마 진화 강력 | 인프라 복잡도 증가, 현 규모 대비 과대 | 보류 (필요 시 재검토) |
| 헤더 기반 라우팅 (선정) | contracts 변경 없이 확장 · Kafka 표준 기능 | 헤더 세팅 책임이 Publisher 측에 있음 | **채택** |

## 결과 (Consequences)

### 긍정
- `contracts` 모듈에 타입 계층 변경 없음. `DomainEvent` 인터페이스와 record 8종만 유지.
- 신규 이벤트 추가 시 **(1)** contracts 에 record 추가 → **(2)** Producer 측 Outbox publisher 가 `event-type` 을 찍어 보냄 → **(3)** Consumer 측 registry 에 등록만 하면 됨. 그 외 변경 없음.
- 미등록 타입에 대해 구독자가 우아하게 실패 (DLQ) 할 수 있어 상호 독립 배포 가능성 향상.

### 부정 · 리스크
- `event-type` 헤더가 **반드시** Publisher 측에서 채워져야 함. `common-infrastructure` 에 `OutboxEventPublisher` 와 `DomainEventKafkaDeserializer` 유틸을 둬 실수를 막는다 (PR-2.x 구현 시 추가).
- Consumer 측 `registry` 가 각 서비스에 분산되어 "어떤 서비스가 어떤 이벤트를 구독하는가" 가 한눈에 보이지 않음. PRD §11 Service Impact 매트릭스와 PR-2.x 의 ArchUnit 규칙으로 보완.

## 구현 일정

- **PR-0.3 (본 문서)**: ADR 기록.
- **PR-2.2 reservation-service 예약 생성**: Outbox publisher 유틸을 `common-infrastructure/messaging/` 에 도입, `event-type` · `event-id` · `occurred-at` 헤더 규약 코드화.
- **PR-3.1 hotel-service Redis 캐시 + reservation-events 구독**: Consumer 측 registry + 역직렬화기 첫 구현.
- **PR-0.4 ArchUnit**: "`DomainEvent` 구현체는 record 여야 함" · "Kafka `ProducerRecord` 직접 사용 금지 (Outbox 만 허용)" 규칙 추가 검토.

## 참조

- PR-0.2 (#2) code-reviewer M3 리뷰 코멘트 (본 ADR 작성 근거)
- PRD §6 (비기능 — 멱등성, at-least-once)
- PRD §9 (Saga 흐름)
- `.claude/skills/module-boundary/SKILL.md` §4 (Kafka 이벤트, Outbox 패턴)

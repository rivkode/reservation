# PRD: 호텔 예약 시스템 (MSA)

> **Status**: Draft
> **Owner**: @team
> **Last updated**: 2026-04-21
> **Architecture**: MSA (모노레포, Gradle 멀티모듈), 서비스별 DDD
> **Communication**: 동기 **gRPC**, 비동기 **Kafka**
> **Storage**: 서비스별 **MySQL** (Database per Service), 읽기 캐시 **Redis**

---

## 1. 배경 (Why)

호텔 예약 시스템을 **MSA로 설계**하여 서비스별 독립 배포, 도메인 격리, 확장성을 확보한다. 각 서비스는 자체 MySQL 스키마와 Gradle 모듈을 가지며, 서비스간 통신은 **gRPC (동기)** 와 **Kafka (비동기)** 를 통해서만 이루어진다. 공유는 `contracts` 모듈의 proto/이벤트 스키마로만 한다.

---

## 2. 목표 / 비목표

### Goals
- 고객이 호텔 객실을 검색·예약·취소할 수 있는 시스템 구축
- 호텔 정보, 요금 정책, 투숙객 정보, 예약을 **독립된 서비스**로 분리
- 서비스별 독립 배포 가능
- 서비스간 데이터 일관성은 최종 일관성(eventual consistency) 허용
- 각 서비스는 Database per Service (MySQL) 원칙
- 가용성 조회는 Redis 캐시로 고속 응답

### Non-Goals
- 결제 처리 (별도 PRD: `002-payment.md` 예정)
- 객실 이미지/리뷰 관리 (v2 범위)
- 다국어 지원 (v2)
- 모바일 앱 (웹 API 우선)
- 추천 / 개인화 (v2)
- 호텔 사업자용 어드민 포털 (별도 PRD)

---

## 3. 서비스 구조 (Bounded Context)

```
┌─────────────────────┐  ┌─────────────────────┐
│   hotel-service     │  │    rate-service     │
│  (호텔/객실 마스터   │  │   (객실 요금 정책)   │
│   + 가용성 조회 캐시) │  │                     │
│                     │  │                     │
│  Aggregate:         │  │  Aggregate:         │
│   - Hotel           │  │   - RoomTypeRate    │
│   - Room            │  │                     │
│  Read Model (Redis):│  │                     │
│   - RoomAvailability│  │                     │
│     View            │  │                     │
└──────────┬──────────┘  └──────────┬──────────┘
           │  hotel-events (Kafka)   │  rate-events (Kafka)
           │                         │
           ▼                         ▼
┌───────────────────────────────────────────┐
│         reservation-service                │
│  (예약 + 객실 재고 — SoT)                  │
│                                            │
│  Aggregate:                                │
│   - RoomTypeInventory  ← 재고 SoT          │
│   - Reservation                            │
│                                            │
│  Kafka 발행: ReservationCreated,           │
│             ReservationCancelled           │
│  gRPC 호출: guest-service (투숙객 검증)     │
└──────────┬────────────────────────────────┘
           │  gRPC 조회
           ▼
┌─────────────────────┐
│   guest-service     │
│   (투숙객 정보)      │
│                     │
│  Aggregate:         │
│   - Guest           │
└─────────────────────┘
```

### 3.1 서비스별 책임

| 서비스 | 책임 | 소유 데이터 |
|---|---|---|
| **hotel-service** | 호텔·객실 마스터 CRUD, 객실 타입 정의, **가용성 조회 Read Model (Redis)** | Hotel, Room (MySQL) + RoomAvailabilityView (Redis) |
| **rate-service** | 객실 타입별 날짜별 요금 정책 관리 | RoomTypeRate (MySQL) |
| **guest-service** | 투숙객 등록, 조회, 수정 | Guest (MySQL) |
| **reservation-service** | 예약 생성/취소, **객실 재고의 Source of Truth** | RoomTypeInventory, Reservation (MySQL) |

### 3.2 재고 소유 원칙 (DDD 분석 근거)

**쓰기 (SoT)**: `RoomTypeInventory` 는 **reservation-service** 가 소유.

**근거**:
1. **Source of Truth**: 재고는 "전체 방 수 − 확정 예약 수" 로 예약 행위에 의해 결정되는 값. 예약 데이터를 소유한 서비스가 재고의 SoT.
2. **트랜잭션 무결성**: 예약 생성 시 `Inventory.decrease()` 와 `Reservation.create()` 가 **같은 MySQL 로컬 트랜잭션**으로 묶여야 오버부킹 방지. 분산 트랜잭션 없이 안전.
3. **취소 보상**: 예약 취소 시 재고 복원도 같은 서비스에서 로컬 트랜잭션으로 처리.

**읽기 (Read Model)**: `RoomAvailabilityView` 는 **hotel-service** 가 소유 (Redis 캐시).

**근거**:
1. **조회 성능**: 가용성 검색은 빈도가 높고 응답 속도 중요 → Redis.
2. **CQRS 패턴**: 쓰기와 읽기를 물리적으로 분리. reservation-service 쓰기 부하와 hotel-service 조회 부하 격리.
3. **이벤트 기반 동기화**: reservation-service 의 `ReservationCreated` / `ReservationCancelled` 이벤트를 구독해 Redis 갱신 (최종 일관성).

**중요 규칙**:
- 이름 구분: 쓰기는 `RoomTypeInventory`, 읽기는 `RoomAvailabilityView`
- **예약 확정 시 반드시 reservation-service 에서 재검증** — 캐시 조회만 믿고 확정하지 않음
- 캐시 재구축 배치 필수 (이벤트 유실 대비)

**Billing 책임 · Saga (Q1 Resolved)**:
예약 확정에 수반되는 `Billing` 생성은 **rate-service** 가 `reservation-events/ReservationCreated` 를 구독해 담당한다. reservation-service 는 예약 생성 시점의 `totalAmount` 견적을 위해서만 `rate.proto/GetRoomTypeRate` 단일 gRPC 를 동기 호출한다 (Deadline 3초 · Circuit Breaker 필수).

- 정상 경로: `ReservationCreated` → rate Billing 생성 → `BillingCreated` → reservation 확정 단계 전이
- 보상 경로: Billing 실패 → `BillingCreationFailed` → reservation 자동 취소 + 재고 복원

상세 근거와 대안 비교는 **[ADR 0003 Saga for Reservation](../adr/0003-saga-for-reservation.md)** · CQRS 근거는 **[ADR 0004 CQRS for RoomAvailability](../adr/0004-cqrs-for-room-availability.md)** 참조.

---

## 4. 사용자 시나리오

- **US-01** (예약자): 날짜와 조건으로 가용 객실 검색 → hotel-service 의 Redis 캐시 조회
- **US-02** (예약자): 객실 선택 후 예약 생성 → reservation-service 에서 SoT 재검증 + 확정
- **US-03** (예약자): 예약 취소 → 재고 복원
- **US-04** (호텔 관리자): 호텔·객실 등록 및 수정
- **US-05** (호텔 관리자): 객실 타입별 요금 설정
- **US-06** (호텔 관리자): 예약 현황 조회

---

## 5. 기능 요구사항 (Functional Requirements)

### FR-Hotel (hotel-service)
- **FR-H-01**: 호텔 등록 (이름, 주소, 등급, 편의시설)
- **FR-H-02**: 호텔 조회 (ID / 조건 검색)
- **FR-H-03**: 객실 등록 (호텔 ID, 객실 타입, 수용 인원)
- **FR-H-04**: 객실 정보 변경 시 `RoomUpdated` 이벤트 발행 (Kafka)
- **FR-H-05**: 객실 삭제 시 `RoomDeleted` 이벤트 발행
- **FR-H-06**: 가용성 조회 API (Redis `RoomAvailabilityView` 조회)
- **FR-H-07**: reservation-events 구독 → Redis 캐시 갱신
- **FR-H-08**: 캐시 재구축 배치 (매일 새벽, reservation-service gRPC 로 전체 Inventory 조회)

### FR-Rate (rate-service)
- **FR-R-01**: 객실 타입별·날짜별 요금 등록
- **FR-R-02**: 특정 호텔·객실 타입·날짜 범위의 요금 조회
- **FR-R-03**: 요금 변경 시 `RoomTypeRateChanged` 이벤트 발행
- **FR-R-04**: reservation-events 구독 → 정산 레코드 생성 (향후 billing 과 연결)

### FR-Guest (guest-service)
- **FR-G-01**: 투숙객 등록 (이름, 연락처, 이메일)
- **FR-G-02**: 투숙객 조회 (ID) — **gRPC 서비스 제공 (GetGuest)**
- **FR-G-03**: 투숙객 정보 변경

### FR-Reservation (reservation-service)
- **FR-RSV-01**: gRPC 서비스 제공 — Inventory 조회/스트리밍 (hotel-service 캐시 재구축용)
- **FR-RSV-02**: 예약 생성 API
  - 동기: guest-service 에 **gRPC** 로 투숙객 검증 (Deadline 3초, Circuit Breaker)
  - 로컬 MySQL 트랜잭션: RoomTypeInventory 차감 + Reservation 생성 + Outbox 적재
  - Kafka: `ReservationCreated` 발행
- **FR-RSV-03**: 예약 취소 (체크인 24시간 전 무료, 이후 위약금 정책)
  - 로컬 MySQL 트랜잭션: Reservation 상태 변경 + RoomTypeInventory 복원 + Outbox 적재
  - Kafka: `ReservationCancelled` 발행
- **FR-RSV-04**: hotel-events 구독 → RoomTypeInventory 레코드 추가/제거
- **FR-RSV-05**: 예약 조회 (ID / 투숙객 ID / 호텔 ID 로)

---

## 6. 비기능 요구사항

- 예약 생성 API 응답 시간 **p99 < 1초** (gRPC 호출 포함)
- 가용성 조회 API 응답 시간 **p99 < 200ms** (Redis 캐시 히트 전제)
- 동일 재고에 대한 동시 예약 요청은 **하나만 성공** (MySQL 낙관적 락, `version` 컬럼)
- 서비스간 이벤트 전달은 **at-least-once** + 구독자 **멱등성** 보장 (MySQL `processed_events` 테이블)
- **모든 gRPC 호출에 Deadline 설정 필수** (기본 3초)
- Circuit Breaker 필수 (Resilience4j)
- 모든 이벤트에 `eventId`, `occurredAt` 포함
- 캐시 stale 허용 시간: **최대 30초** (이벤트 처리 지연 포함)

---

## 7. 서비스간 통신 계약 (`contracts` 모듈)

### 7.1 gRPC 서비스 (Proto)

| 파일 | 제공 서비스 | 호출자 | 주요 RPC |
|---|---|---|---|
| `guest.proto` | guest-service | reservation-service | `GetGuest`, `BatchGetGuests` |
| `rate.proto` | rate-service | reservation-service | `GetRoomTypeRate` (예약 생성 시 견적) |
| `reservation.proto` | reservation-service | hotel-service | `StreamInventory` (캐시 재구축) |
| `hotel.proto` | hotel-service | (향후) | `GetHotel`, `GetRoomType` |

### 7.2 Kafka 이벤트

| Topic | 발행자 | 구독자 | 이벤트 타입 |
|---|---|---|---|
| `hotel-events` | hotel-service | reservation-service | `RoomCreated`, `RoomUpdated`, `RoomDeleted` |
| `rate-events` | rate-service | (향후 billing) | `RoomTypeRateChanged` |
| `reservation-events` | reservation-service | hotel-service, rate-service, guest-service | `ReservationCreated`, `ReservationCancelled` |
| `billing-events` | rate-service | reservation-service | `BillingCreated`, `BillingCreationFailed` |

---

## 8. API 설계 (주요)

### 8.1 예약 생성 (reservation-service)

```
POST /api/v1/reservations
Body:
{
  "hotelId": "H-001",
  "roomTypeId": "RT-DLX",
  "guestId": "G-42",
  "checkInDate": "2026-06-01",
  "checkOutDate": "2026-06-03",
  "numberOfGuests": 2
}

Response 201:
{
  "reservationId": "R-20260601-001",
  "status": "CONFIRMED",
  "totalAmount": 300000
}

Errors:
  400 - 유효하지 않은 날짜 / 잘못된 투숙객 ID
  404 - 호텔/객실 타입/투숙객 없음
  409 - 재고 부족 (SoT 재검증 실패)
  503 - 외부 서비스(guest-service) 장애 (Circuit Breaker 발동)
```

### 8.2 가용성 조회 (hotel-service)

```
GET /api/v1/availability?hotelId=H-001&roomTypeId=RT-DLX&checkIn=2026-06-01&checkOut=2026-06-03

Response 200:
{
  "hotelId": "H-001",
  "roomTypeId": "RT-DLX",
  "availability": [
    { "date": "2026-06-01", "available": 3, "total": 10 },
    { "date": "2026-06-02", "available": 3, "total": 10 }
  ],
  "staleUntil": "2026-04-21T10:00:30Z"
}
```

**주의**: 응답은 Redis 캐시 기반으로 stale 할 수 있음. 예약 확정 시 reservation-service 가 재검증.

---

## 9. 데이터 일관성 전략 (Saga)

### 9.1 예약 생성 Saga

```
reservation-service: 로컬 트랜잭션(재고 차감 + 예약 생성 + Outbox) → ReservationCreated 발행
   ├─ hotel-service: Redis 가용성 캐시 감소 (멱등)
   ├─ rate-service: 정산 레코드 생성 → 실패 시 BillingCreationFailed 발행
   │    └ reservation-service: 예약 자동 취소 (보상 트랜잭션, 재고 복원)
   └─ guest-service: 방문 이력 업데이트 (멱등)
```

### 9.2 호텔 정보 동기화

```
hotel-service: Room 변경 → RoomCreated/Updated/Deleted 이벤트 발행
   └ reservation-service: Inventory 레코드 갱신 (멱등)
          └ (ReservationCreated/Cancelled 이벤트로 다시 hotel-service 캐시 갱신)
```

**모든 구독자는 `eventId` + MySQL `processed_events` 테이블로 멱등성 보장**.

---

## 10. 측정 지표

- 예약 API 성공률 99.5%
- 예약 → 캐시 갱신 지연 p99 < 5초
- gRPC 호출 실패율 < 0.5%
- Circuit Breaker 발동 횟수 (Grafana 대시보드)
- Redis 캐시 히트율 > 99%

---

## 11. Service Impact

| 기능 | hotel | rate | guest | reservation | contracts |
|---|:---:|:---:|:---:|:---:|:---:|
| 호텔 등록 | ✅ | | | | - |
| 객실 등록 | ✅ | | | ✅ (Inventory 생성) | 이벤트 |
| 객실 삭제 | ✅ | | | ✅ (Inventory 제거) | 이벤트 |
| 요금 설정 | | ✅ | | | 이벤트 |
| 투숙객 등록 | | | ✅ | | proto |
| 예약 생성 | ✅ (이벤트 구독 → 캐시 갱신) | ✅ (이벤트 구독 → Billing 생성, gRPC `GetRoomTypeRate` 제공) | ✅ (gRPC `GetGuest` 제공 + 이력 갱신) | ✅ | proto (2) + 이벤트 |
| 예약 취소 | ✅ (캐시 복원) | ✅ (Billing 취소) | ✅ (이력 롤백) | ✅ | 이벤트 |
| 가용성 조회 | ✅ | | | | proto (간접) |
| 캐시 재구축 배치 | ✅ | | | ✅ (`StreamInventory` gRPC 제공) | proto |

---

## 12. 구현 분할 (PR 단위)

### Phase 0: 기반 구축
- **PR-0.1**: Gradle 멀티모듈 골격 (`settings.gradle.kts`, 서비스별 `build.gradle.kts`)
- **PR-0.2**: `contracts` 모듈 (protobuf-gradle-plugin + 이벤트 record 베이스)
- **PR-0.3**: `common-infrastructure` 모듈 (공통 Kafka/gRPC 설정, 예외, 로깅)
- **PR-0.4**: ArchUnit 규칙 세팅 (계층 + 서비스 경계 + gRPC/Kafka 위치)
- **PR-0.5**: Docker Compose (MySQL × 4, Kafka, Zookeeper, Redis) + 각 서비스 부팅 확인

### Phase 1: 서비스별 기본 CRUD (병렬 가능)
- **PR-1.1**: hotel-service — Hotel / Room Aggregate + REST API + 이벤트 발행
- **PR-1.2**: rate-service — RoomTypeRate Aggregate + REST API + 이벤트 발행
- **PR-1.3**: guest-service — Guest Aggregate + REST API + **gRPC 서비스 제공**

### Phase 2: 예약 서비스
- **PR-2.1**: reservation-service — RoomTypeInventory Aggregate + hotel-events 구독
- **PR-2.2**: reservation-service — Reservation + 예약 생성 API (gRPC 클라이언트 + Outbox)
- **PR-2.3**: reservation-service — 예약 취소 API + 보상 트랜잭션 리스너
- **PR-2.4**: reservation-service — Inventory gRPC 서비스 제공 (캐시 재구축용)

### Phase 3: 읽기 경로
- **PR-3.1**: hotel-service — Redis 가용성 캐시 + reservation-events 구독 (캐시 갱신)
- **PR-3.2**: hotel-service — 가용성 조회 API
- **PR-3.3**: hotel-service — 캐시 재구축 배치 (reservation-service gRPC 스트리밍)

### Phase 4: 안정화
- **PR-4.1**: Resilience4j Circuit Breaker + Retry 정비
- **PR-4.2**: E2E 통합 테스트 (Testcontainers: MySQL + Kafka + Redis)
- **PR-4.3**: 관측 (로깅, 트레이싱, 메트릭)

---

## 13. 열린 질문 · 결정 (Resolved 2026-04-21)

초안 시점의 open questions 는 Plan 문서 §2 에서 모두 합의되었으며, 아래에 답안을 병합한다. 상세 근거는 관련 ADR 참조.

- [x] **Q1. 요금 계산 주체**: reservation-service 가 rate-service gRPC 조회? 또는 rate-service 가 이벤트 구독 후 별도 Billing 생성?
  **답안**: **이벤트 구독 + 최소 동기 조회**. rate-service 가 `ReservationCreated` 를 구독해 Billing 생성 · `BillingCreated`/`BillingCreationFailed` 발행. 예약 생성 시 `totalAmount` 견적은 `rate.proto/GetRoomTypeRate` 단일 RPC 로 동기 조회. Saga 보상: 실패 시 예약 자동 취소. → **[ADR 0003](../adr/0003-saga-for-reservation.md)**

- [x] **Q2. 투숙객 조회 실패 정책**: Strict (차단) vs Lenient (일단 허용 후 보정)?
  **답안**: **Strict**. Circuit Breaker 발동 시 `503 EXTERNAL_SERVICE_UNAVAILABLE` 반환. 근거: guest 정보 없는 예약은 체크인 · 커뮤니케이션 · 결제 연결 전부 불가.

- [x] **Q3. 취소 위약금 정책 상세**: 시간대별 %.
  **답안**: **초기 2단계** — 체크인 24시간 전까지 환불 100% · 이내 환불 0%. `CancellationPolicy` VO 로 추상화해 향후 72h/24h/당일 차등 확장 가능. 실제 환불 처리는 별도 결제 PRD (`002-payment.md`) 확정 전까지 "상태 기록만".

- [x] **Q4. Kafka 파티션 키 전략**: 호텔별 / 예약별?
  **답안**: **토픽별 상이**. `hotel-events` · `rate-events` · `reservation-events` 는 `hotelId` (동일 호텔 이벤트 순서 보장 · 구독자 부하 분산). `billing-events` 는 `reservationId` (예약 단위 Saga 응답 순서 보장).

- [x] **Q5. 공개 API Gateway 필요 여부**
  **답안**: **이번 PRD 범위 외**. Q6 와 연동되므로 인증 PRD 에서 함께 결정. 로컬은 각 서비스 포트 직접 노출, 운영은 Ingress/LB.

- [x] **Q6. 인증/인가 구현 범위**: 이번 PRD? 별도?
  **답안**: **별도 PRD 로 분리** (OAuth2 · JWT · Role 매트릭스). 이번 PRD 는 임시로 `X-Guest-Id` 헤더로 guest 식별. 관리자 API 는 최소 IP 제한 + 외부 노출 금지.

- [x] **Q7. 캐시 TTL 정책 상세**: 기본 48시간? 짧게?
  **답안**: **TTL 없음**. 이벤트 기반 갱신(`ReservationCreated/Cancelled` 구독) + 일일 배치 재구축 (02:00 KST, `StreamInventory` gRPC) + **90일 범위만 캐시**. Redis `maxmemory-policy=noeviction`. → **[ADR 0004](../adr/0004-cqrs-for-room-availability.md)**

---

## 14. 진행 현황

- [x] PR-0.1: 멀티모듈 골격
- [x] PR-0.2: contracts (proto + event)
- [x] PR-0.3: common-infrastructure
- [x] PR-0.4: ArchUnit 규칙
- [x] PR-0.5: Docker Compose + 부팅 확인
- [x] PR-1.1: hotel-service
- [x] PR-1.2: rate-service
- [x] PR-1.3: guest-service (+ gRPC)
- [x] PR-2.1: reservation-service Inventory
- [x] PR-2.2: reservation-service 예약 생성
- [x] PR-2.3: reservation-service 예약 취소
- [ ] PR-2.4: reservation-service Inventory gRPC
- [ ] PR-3.1: hotel-service Redis 캐시
- [ ] PR-3.2: hotel-service 가용성 조회 API
- [ ] PR-3.3: hotel-service 캐시 재구축 배치
- [ ] Phase 4 작업

---

## 15. 참고

- ADR (예정):
  - `docs/adr/0001-service-boundaries.md`
  - `docs/adr/0002-grpc-for-sync-kafka-for-async.md`
  - `docs/adr/0003-saga-for-reservation.md`
  - `docs/adr/0004-cqrs-for-room-availability.md`
- 아키텍처 다이어그램: `docs/architecture/msa-overview.md`

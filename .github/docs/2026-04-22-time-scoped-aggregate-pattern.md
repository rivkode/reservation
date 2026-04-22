# Time-scoped Aggregate 패턴 — RoomTypeRate · RoomTypeInventory 공통 설계

- **날짜**: 2026-04-22
- **관련 PR**: PR-1.2 rate-service (예정) · PR-2.1 reservation-service (예정)
- **관련 ADR**: [`0004-cqrs-for-room-availability`](../../docs/adr/0004-cqrs-for-room-availability.md) — CQRS 읽기/쓰기 분리의 쓰기 SoT 가 본 패턴
- **관련 PRD 섹션**: §5.2 FR-R-01 (객실 타입별·날짜별 요금 등록) · §5.5 FR-RSV-04 (Inventory 레코드 추가/제거) · §9.1 (RoomTypeInventory 복합키)

## 배경

Phase 1 이후 구현될 두 Aggregate 가 **동일한 복합 PK 구조** 를 가진다:

- `RoomTypeRate` (rate-service)
- `RoomTypeInventory` (reservation-service)

두 Aggregate 모두 PK 가 `(hotelId, roomTypeId, date)` 이다. 구현을 서로 다른 서비스에서 독립 진행하기 전에 "**왜 date 를 PK 에 포함하는가**", "**두 Aggregate 의 공통점·차이점**", "**레코드가 없는 날짜를 어떻게 취급할 것인가**" 를 한 번에 고정해 세 PR (PR-1.1 / PR-1.2 / PR-2.1) 이 일관되게 구현되도록 한다.

## 현재 상태

- PRD §5.2 FR-R-01: "객실 타입별·날짜별 요금 등록"
- PRD §5.5 FR-RSV-04: "hotel-events 구독 → RoomTypeInventory 레코드 추가/제거"
- 이전 Plan §3.4: `RoomTypeRate` 식별자 = `(hotelId, roomTypeId, date)` 복합키 VO `RateKey`
- 이전 Plan §3.5: `RoomTypeInventory` 식별자 = `(hotelId, roomTypeId, date)` 복합키 VO `InventoryKey`
- `contracts/rate.proto` · `contracts/reservation.proto` 이미 `date` 필드 포함
- ADR 0004 가 쓰기 SoT (MySQL `room_type_inventory`) vs 읽기 Read Model (Redis) 분리는 확정했지만 **PK 설계 자체** 는 명시적 문서화가 없었음 — 본 문서가 보강

## 분석

### A. 왜 복합키 `(hotelId, roomTypeId, date)` 인가?

**호텔 예약 업계의 본질적 가격·재고 모델을 그대로 반영**.

- 요금: 같은 Deluxe 라도 **5/1 (평일) 150,000원** · **5/10 (주말) 200,000원** · **12/25 300,000원**
- 재고: "방 3개" 가 아니라 "**5/1 에 3개 · 5/2 에 3개 · …**" — 날짜마다 예약 가능 수량 독립
- 비즈니스가 "어느 호텔 × 어느 타입 × 어느 날짜" 단위로 의사결정하므로 Aggregate Root 의 식별자도 이 세 축

### B. 대안 구조 (기각)

| 대안 | 문제 |
|---|---|
| `Rate` Aggregate 에 `Map<Date, Money>` | Aggregate load 시 1년 치 row 전체 메모리 로드. 한 날짜 수정에도 Aggregate 전체 재저장. Aggregate 크기 폭발 |
| 별도 `PriceCalendar` Entity 참조 | 단일 Aggregate 경계 흐려짐. 트랜잭션 경계 모호 |
| surrogate ID (Long auto-increment) + `(hotel, type, date)` unique index | PK 와 업무 식별자 이원화. 의미 없는 FK · JOIN 복잡. 이득 없이 저장 공간만 낭비 |
| MongoDB 같은 document 에 호텔별 문서로 저장 | RDBMS + JPA 전제 · 원자적 트랜잭션 (예약 + 재고 차감) 깨짐 |

복합키 PK 가 DDD 상 가장 자연스럽고 성능·트랜잭션·일관성 측면에서도 우세.

### C. 두 Aggregate 의 속성 비교

| 속성 | RoomTypeRate (Pricing BC) | RoomTypeInventory (Inventory BC) |
|---|---|---|
| PK | `(hotelId, roomTypeId, date)` | `(hotelId, roomTypeId, date)` ✅ 동일 |
| 단위 | 날짜별 **가격** | 날짜별 **재고** |
| 특징 값 | `amount (long) · currency (String, ISO-4217)` → `Money` VO | `totalInventory (int) · totalReserved (int)` |
| 소유 서비스 | rate-service | reservation-service (SoT) |
| 변경 빈도 | 낮음 (관리자 주기 조정) | **매우 높음** (예약마다 차감 · 취소마다 복원) |
| 동시성 | 낮음 (보통 admin 단일 요청) | **매우 높음** (동일 재고에 동시 예약) |
| 낙관적 락 `version` | 있으면 좋음 | **필수** (CAS 로 오버부킹 방지) |
| 불변식 | `amount >= 0` | `0 ≤ totalReserved ≤ totalInventory × 1.10` (오버부킹 110%) |
| 도메인 이벤트 | `RoomTypeRateChanged` | (간접) `ReservationCreated/Cancelled` |
| gRPC 외부 노출 | `GetRoomTypeRate` (Phase 2) | `StreamInventory` (Phase 3 캐시 재구축 용) |

### D. 같은 패턴 · 다른 책임

- **같은 이유**: 두 Aggregate 모두 "호텔업 리소스는 날짜 단위로 존재" 라는 동일한 비즈니스 원리
- **다른 이유**: Inventory 는 **트랜잭션 경쟁의 무대** (오버부킹 방지를 위한 CAS · 낙관적 락 · 동시성 제어) 인 반면 Rate 는 단순 CRUD

## 결정

### 1. Time-scoped Aggregate 패턴 공식 채택

두 Aggregate 모두 **`(hotelId, roomTypeId, date)` 복합 PK 의 Per-day Aggregate** 로 구현한다. 두 서비스가 각자의 Bounded Context 에서 **구조를 공유**하되 **VO 타입은 분리** 한다 (`RateKey` vs `InventoryKey`) — common-infra 로 끌어올리지 않는다. 이유는 Bounded Context 경계 보존 (CLAUDE.md 원칙 #1).

### 2. 결측 날짜 정책: **옵션 A (단순 404)**

해당 `(hotelId, roomTypeId, date)` 레코드가 없으면:

- rate-service: `DailyRateNotFoundException` · HTTP 404 (`RESOURCE_NOT_FOUND`)
- reservation-service: 예약 생성 시 Inventory 레코드가 없으면 `409 CONFLICT` 또는 `404 NOT_FOUND` (구현 시점 재결정, 권장은 404)
- 재고 0 (`available == 0`) 과 레코드 부재는 **의미가 다르다**:
  - 재고 0 = "등록된 재고가 소진" → 취소 대기 · 다른 날짜 추천 가능
  - 레코드 없음 = "해당 날짜는 가격/재고 정책 자체가 등록 안 됨" → 운영 배치 누락 · 예약 자체 불가

#### 옵션 A 를 선택한 이유

| 관점 | 옵션 A (단순 404) | 옵션 B (fallback 정책) |
|---|---|---|
| 구현 복잡도 | 단순 (record 존재 여부만) | fallback 정책 엔진 · 테이블 구조 추가 |
| 운영 가시성 | 404 가 배치 누락 신호로 직접 작용 | fallback 이 숨겨 버림 · 운영자가 문제 인지 늦음 |
| 예측 가능성 | 관리자가 등록한 값만 예약 가능 | 자동 추정 값으로 예약 → 나중에 환불/분쟁 리스크 |
| PRD 명시 | 없음 | 없음 |

- **운영 규칙**: 배치가 미래 **90일치** 를 선제 등록한다는 기본 가정 (ADR 0004 의 캐시 범위와 일치)
- 운영 중 배치 실패로 결측 발생 → 404 가 즉시 드러남 → 관찰 가능성 > 가시성 은닉

### 3. 과거 날짜 정리

- 둘 다 체크아웃 경과 이후의 과거 레코드는 **즉시 삭제하지 않는다** (예약 분쟁 · 감사용)
- 월 단위 archive 배치는 Phase 4+ 운영 PR 로 연기

## 결과 · 후속

### PR-1.1 hotel-service
- Room Aggregate 에는 본 패턴 **적용되지 않음** (Room 자체는 물리 객체 · 날짜 무관)
- 단, `RoomCreated`/`RoomDeleted` 이벤트 구독자인 reservation-service 가 Inventory 레코드를 생성/삭제할 때 본 패턴을 적용

### PR-1.2 rate-service
- `RoomTypeRate` Aggregate 와 `RateKey` VO 구현
- `DailyRateNotFoundException` (404 매핑) 을 Presentation 에서 처리
- Flyway V1 `room_type_rate` 테이블 스키마: 복합 PK + amount + currency + version

### PR-2.1 reservation-service Inventory
- `RoomTypeInventory` Aggregate 와 `InventoryKey` VO 구현
- `reserve(int)` / `release(int)` 메서드에 오버부킹 110% 불변식
- 낙관적 락 `@Version` 필수
- Flyway V1 `room_type_inventory` 테이블: 복합 PK + total_inventory + total_reserved + version

### PR-2.2 reservation-service 예약 생성
- 예약 생성 시 `(hotelId, roomTypeId, [checkIn..checkOut))` 범위 전부에 대해 Inventory 레코드 존재 여부 먼저 확인
- 하나라도 없으면 404 또는 409 (결정은 PR-2.2 에서) — "예약 불가 날짜 포함" 오류
- Rate 조회도 날짜별 gRPC 반복 호출 (ADR 0002 결정)

## 이 결정을 뒤집어야 할 조건

- 호텔 사업자 UX 피드백에서 "유연한 기본 요금 (fallback) 이 필요하다" 가 강력히 제기되면 옵션 B 재검토. 단 그 시점엔 **별도 `DefaultRate` Aggregate** 를 도입하고 `RoomTypeRate` 는 overrides 로 재정의하는 편이 깔끔.
- 재고 결측을 "자동으로 0 으로 간주" 해야 하는 요구가 생기면 예약 로직이 레코드 부재와 재고 0 을 동일 취급 — 현재는 **명시적 레코드** 가 예약 가능 조건이다.

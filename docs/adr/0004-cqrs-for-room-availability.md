# ADR 0004 — 가용성 조회를 위한 CQRS (쓰기 SoT vs 읽기 Read Model)

- **상태**: Accepted
- **작성일**: 2026-04-22
- **관련 PR**: PR-docs-2 · PR-3.1 hotel-service Redis 캐시 + reservation-events 구독 (예정)

## 맥락

호텔 예약의 두 주요 유스케이스:
- **(A) 가용성 조회** — 읽기 빈도 높음 · p99 < 200ms (PRD §6)
- **(B) 예약 생성** — 쓰기 · 재고 정합성 (오버부킹 방지) 최우선

두 요구가 같은 저장소에 결합되면 읽기 부하가 쓰기 locking 에 영향을 주거나, 읽기 인덱스 설계와 쓰기 트랜잭션 일관성이 충돌.

## 결정

**CQRS (Command Query Responsibility Segregation)** 로 쓰기와 읽기를 **물리적으로 분리**.

| 측면 | 쓰기 (SoT) | 읽기 (Read Model) |
|---|---|---|
| 담당 서비스 | **reservation-service** | **hotel-service** |
| 저장소 | MySQL `room_type_inventory` | Redis `RoomAvailabilityView` |
| 타입명 | `RoomTypeInventory` (Aggregate) | `RoomAvailabilityView` (DTO) |
| 트랜잭션 | 로컬 MySQL (재고 차감 + 예약 생성 + Outbox) | 없음 (eventual) |
| 일관성 | 강 일관성 (낙관적 락 `version`) | 최종 일관성 (≤ 30s stale) |
| 동기화 | (쓰기) | `reservation-events` 구독 + 일일 배치 재구축 |

### 키 · 값 규약 (Redis)

- key: `avail:{hotelId}:{roomTypeId}:{YYYY-MM-DD}`
- value: JSON `{ "available": int, "total": int, "updatedAt": ISO-8601 }`
- TTL: **없음** (Plan Q7)
- 90일 범위 외 key 는 일일 배치가 삭제

### 재검증 원칙

- **예약 확정은 반드시 reservation-service 쓰기 SoT 에서 재검증** (캐시 신뢰 금지)
- 가용성 조회는 Redis 만 조회 (gRPC fallback 없음 — reservation-service 과부하 방지)

## 근거

- **읽기 · 쓰기 부하 격리**: reservation 쓰기 QPS 와 hotel 읽기 QPS 가 서로 간섭 X
- **저장 기술 최적화**: 쓰기는 트랜잭션 필요 → MySQL, 읽기는 단순 key lookup → Redis 최적
- **최종 일관성 수용**: 가용성 조회의 stale 30s 는 제품 허용 범위 (PRD §6), 예약 확정은 SoT 재검증으로 강 일관성

## 대안 (기각)

- **단일 MySQL 에서 조회 + 쓰기**: 읽기 인덱스와 쓰기 트랜잭션 경합 · 조회 p99 200ms 충족 어려움
- **MySQL 읽기 Replica**: 복제 지연이 Redis 보다 크고 key lookup 패턴 최적화 약함
- **Redis 만 사용 (SoT 없음)**: 오버부킹 방지 강 일관성 · 영속 내구성 부족

## 운영 정책

### 캐시 동기화

- `reservation-events/ReservationCreated` → `available -= numberOfGuests` (멱등 CAS)
- `reservation-events/ReservationCancelled` → `available += numberOfGuests` (멱등 CAS)
- `hotel-events/RoomCreated` · `RoomDeleted` → hotel-service 가 Inventory 초기화 · 제거 시 캐시 entry 생성 · 삭제

### 배치 재구축

- **매일 02:00 KST** — hotel-service 가 reservation-service `StreamInventory` gRPC 호출해 90일 범위 전체 재구축
- 이벤트 유실 감지 · 데이터 드리프트 안전망

### 장애 대응

- **Redis 장애**: 가용성 조회 API 는 `503 EXTERNAL_SERVICE_UNAVAILABLE` 반환
- **이벤트 지연 > 30s**: 응답에 `staleUntil` 표기 (PRD §8.2)
- 복구 후 배치로 재구축

## 결과

- hotel-service 에 `spring-boot-starter-data-redis` 의존성 추가 예정 (PR-3.1)
- `RoomAvailabilityView` 는 도메인 객체가 아닌 Presentation/Infrastructure DTO
- `StreamInventory` gRPC 는 **reservation-service 가 지연 없이 응답 가능한 크기** (90일 범위 · 호텔 단위) 로 scope 제한

## 참조

- PRD §3.2 재고 소유 원칙 · §10 측정 지표
- Plan Q7 (캐시 TTL) 답안
- ADR 0001 DomainEvent · ADR 0002 통신 · ADR 0003 Saga
- `.claude/skills/module-boundary/references/read-model-sync.md`

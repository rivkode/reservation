# 학습 노트: 서비스 · API 별 비즈니스 시나리오

> **Status**: Learning Note — API 레벨 동작 교본
> **작성일**: 2026-04-23
> **대상**: 현재 구현된 서비스의 모든 REST API
> **참고**: [PRD](../prd/hotel_reservation_prd.md) · [001 분석 노트](./001-hotel-rate-service-split-analysis.md)

---

## 0. 이 문서의 목적

각 서비스의 REST API 를 **비즈니스 시나리오 → 요청 → 응답 → 내부 동작 (도메인 · 이벤트 · DB) → 에러 케이스** 순으로 한 번에 볼 수 있게 정리한다.

- 로컬 기동 후 **어떤 흐름을 검증해야 하는지** 를 체크리스트로 쓸 수 있다.
- **각 API 호출이 트리거하는 이벤트 · DB side-effect** 까지 따라가 "이 엔드포인트를 호출하면 시스템 어디가 변하는가" 를 이해한다.
- 향후 guest-service / reservation-service 추가 시 같은 포맷으로 확장.

---

## 1. 공통 규약

### 1-1. 응답 포맷
- **성공**: `{ "data": <payload> }` (CommonResponse 래퍼, `CommonResponse<T>`)
- **에러**: `{ "timestamp": ..., "status": <int>, "code": "UPPERCASE_ENUM", "message": ..., "path": ..., "fieldViolations": [] }` (ErrorResponse, 래퍼 없음)

### 1-2. HTTP Status
| 의미 | Status | 사용처 |
|---|---|---|
| 생성 | 201 | POST 성공 |
| 조회 / 갱신 | 200 | GET / PATCH 성공 |
| 삭제 (soft-delete) | 200 + `data=null` | DELETE 성공 (204 대신 200 채택) |
| 잘못된 입력 | 400 | `VALIDATION_FAILED` |
| 리소스 없음 | 404 | `*_NOT_FOUND` |
| 충돌 (중복 · 전이 위반) | 409 | `DUPLICATE_*` · `*_MISMATCH` · `INVALID_*_TRANSITION` |
| 서버 에러 | 500 | `INTERNAL_ERROR` |

### 1-3. 식별자
- 모든 Aggregate ID 는 **UUID v7** (BINARY(16) 로 MySQL 저장).
- 외부에는 표준 UUID 문자열 형식 (`01970000-0000-7000-8000-000000000001`).

### 1-4. Kafka 토픽
| 토픽 | 발행자 | 파티션 수 | 파티션 키 | 현재 구독자 |
|---|---|---|---|---|
| `hotel-events` | hotel-service | 3 | `hotelId` | (예정) reservation-service |
| `rate-events` | rate-service | 3 | `hotelId` | (예정) reservation-service · billing |
| `reservation-events` | reservation-service | 3 | `hotelId` | (예정) hotel · rate · guest |
| `billing-events` | rate-service | 3 | `reservationId` | (예정) reservation-service |

### 1-5. Outbox 동작 원리 (모든 이벤트 발행이 타는 경로)
```
[Controller]
  → Application Service @Transactional
    → Domain 상태 변경 (AR)
    → Repository.save(AR)           ┐
    → OutboxEventPublisher.publish( │ 같은 로컬 MySQL 트랜잭션에서 커밋
         DomainEvent, topic, key)   │ (Propagation.MANDATORY 로 강제)
      → OutboxRepository.save       ┘
[별도 스레드] OutboxRelay (@Scheduled)
  → findUnpublished(limit=100)
  → KafkaTemplate.send(topic, key, payload)
  → markPublished(id, now)
```

**보장**:
- 비즈니스 상태 변경과 이벤트 적재가 **원자적** (같은 트랜잭션)
- Kafka 전송 실패 시 재시도 가능 (`published_at IS NULL` 로 필터)
- 멱등성: consumer 측이 `eventId` + `processed_events` 테이블로 보장 (구독 구현 시)

---

## 2. hotel-service (Port 8081)

**Bounded Context**: 호텔 · 객실 타입 · 객실 마스터 데이터 관리
**Aggregate 3개**: `Hotel`, `RoomType`, `Room`
**DB**: MySQL `hotel` 스키마 (hotel-db:3307)

### 📋 API 요약표

| # | API | 메서드 | 경로 | 이벤트 발행 | 핵심 검증 |
|---|---|---|---|---|---|
| 2-1 | 호텔 등록 | POST | `/api/v1/hotels` | — | starRating ∈ [1,5], 주소 non-blank |
| 2-2 | 호텔 조회 | GET | `/api/v1/hotels/{hotelId}` | — | 존재 |
| 2-3 | 객실 타입 등록 | POST | `/api/v1/room-types` | — | Hotel 존재, (hotel, name) UNIQUE |
| 2-4 | 객실 타입 조회 | GET | `/api/v1/room-types/{roomTypeId}` | — | 존재 |
| 2-5 | 객실 타입 목록 | GET | `/api/v1/room-types?hotelId=` | — | Hotel 존재 |
| 2-6 | 객실 등록 | POST | `/api/v1/rooms` | `RoomCreatedEvent` | Hotel · RoomType 존재, 동일 호텔, (hotel, floor, number) UNIQUE |
| 2-7 | 객실 조회 | GET | `/api/v1/rooms/{roomId}` | — | 존재 |
| 2-8 | 객실 목록 | GET | `/api/v1/rooms?hotelId=` | — | Hotel 존재 |
| 2-9 | 객실 타입 변경 | PATCH | `/api/v1/rooms/{roomId}` | `RoomUpdatedEvent` | 새 RoomType 이 같은 호텔 소속 |
| 2-10 | 객실 soft-delete | DELETE | `/api/v1/rooms/{roomId}` | `RoomDeletedEvent` (멱등) | 상태 전이 규칙 |

---

### 2-1. 호텔 등록 — POST /api/v1/hotels

**비즈니스 시나리오**: 호텔 체인 관리자가 신규 오픈 호텔을 시스템에 최초 등록. 주소 · 등급 · 기본 편의시설을 입력한다.

**요청**:
```bash
curl -X POST http://127.0.0.1:8081/api/v1/hotels \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Grand Seoul Hotel",
    "addressStreet": "5 Jongno",
    "addressCity": "Seoul",
    "addressCountry": "KR",
    "starRating": 5,
    "amenities": ["WIFI","POOL","GYM"]
  }'
```

**응답 (201)**:
```json
{
  "data": {
    "id": "01970000-...-uuidv7",
    "name": "Grand Seoul Hotel",
    "addressStreet": "5 Jongno",
    "addressCity": "Seoul",
    "addressCountry": "KR",
    "starRating": 5,
    "amenities": ["WIFI","POOL","GYM"]
  }
}
```

**내부 동작**:
1. `HotelName("Grand Seoul Hotel")` · `HotelAddress(...)` · `StarRating(5)` VO 생성 — 각 생성자가 불변식 검증
2. `Amenity.valueOf("WIFI")` 등으로 Enum 변환 (unknown 값이면 `IllegalArgumentException`)
3. `Hotel.create(...)` → 새 `HotelId` (UUIDv7) 발급, `createdAt = updatedAt = now`
4. `hotelRepository.save(hotel)` → `hotel` + `hotel_amenity` 테이블 INSERT
5. **이벤트 발행 없음** — 현재 Hotel 구독자가 없어 outbox 도 생략 (PRD §7.2 `hotel-events` 는 **Room** 변경만)

**주요 에러**:
| 상황 | Status | code |
|---|---|---|
| `starRating=0` 또는 `6` | 400 | VALIDATION_FAILED |
| `name: ""` (blank) | 400 | VALIDATION_FAILED |
| `amenities=["FOO"]` (unknown enum) | 400 | VALIDATION_FAILED |

**허용되는 "느슨한" 지점**:
- 동일 이름 · 동일 주소의 호텔 **중복 허용** (실 세계에 체인점이 있으므로)
- `amenities` 빈 배열 허용

**Amenity enum 값**: `WIFI`, `PARKING`, `POOL`, `GYM`, `RESTAURANT`, `BAR`, `SPA`, `AIR_CONDITIONING`, `LAUNDRY`, `PET_FRIENDLY`

---

### 2-2. 호텔 조회 — GET /api/v1/hotels/{hotelId}

**시나리오**: 특정 호텔 상세 정보를 조회 (관리자 화면 / 예약 화면의 호텔 정보 박스).

**요청**:
```bash
curl http://127.0.0.1:8081/api/v1/hotels/$HOTEL_ID
```

**응답 (200)**: 2-1 과 동일 포맷.

**주요 에러**:
| 상황 | Status | code |
|---|---|---|
| 존재하지 않는 ID | 404 | HOTEL_NOT_FOUND |
| UUID 형식이 아닌 문자열 | 400 | VALIDATION_FAILED |

---

### 2-3. 객실 타입 등록 — POST /api/v1/room-types

**시나리오**: 호텔 마스터가 등록된 뒤, 해당 호텔에 **"Standard / Deluxe / Suite"** 같은 객실 타입 카탈로그를 정의. 각 타입별로 최대 수용 인원이 고정.

**요청**:
```bash
curl -X POST http://127.0.0.1:8081/api/v1/room-types \
  -H 'Content-Type: application/json' \
  -d "{\"hotelId\":\"$HOTEL_ID\",\"name\":\"Deluxe\",\"maxOccupancy\":2}"
```

**응답 (201)**:
```json
{
  "data": {
    "id": "01970000-...",
    "hotelId": "01970000-...",
    "name": "Deluxe",
    "maxOccupancy": 2
  }
}
```

**내부 동작**:
1. `hotelRepository.existsById(hotelId)` — Hotel 존재 선제 검증 (없으면 404)
2. `roomTypeRepository.existsByHotelIdAndName(hotelId, name)` — 애플리케이션 레벨 UNIQUE 검증 (race condition 최종 방어는 DB 의 `uk_room_type_hotel_name`)
3. `RoomType.create(...)` → 신규 AR
4. save

**주요 에러**:
| 상황 | Status | code |
|---|---|---|
| hotelId 가 존재하지 않음 | 404 | HOTEL_NOT_FOUND |
| 같은 호텔에 같은 name 이 이미 존재 | 409 | DUPLICATE_ROOM_TYPE_NAME |
| `maxOccupancy ≤ 0` | 400 | VALIDATION_FAILED |

---

### 2-4. 객실 타입 조회 — GET /api/v1/room-types/{roomTypeId}
단건 조회. 404 / 400 만 처리.

### 2-5. 객실 타입 목록 — GET /api/v1/room-types?hotelId=

**시나리오**: 호텔 상세 페이지에서 "이 호텔의 객실 타입들" 을 표시.

**응답**: `{"data": [ {...}, {...} ]}` — 0건이면 빈 배열.

주의: hotelId 는 필수. 없으면 400. Hotel 이 없으면 404.

---

### 2-6. 객실 등록 — POST /api/v1/rooms ⭐ 이벤트 발행 O

**비즈니스 시나리오**: 특정 호텔 + 객실 타입에 **물리 객실(301호)** 한 칸을 등록. 이때 reservation-service 는 이 이벤트를 구독해 **Inventory 레코드를 증가** 시킨다 (PRD FR-RSV-04, 후속 PR).

**요청**:
```bash
curl -X POST http://127.0.0.1:8081/api/v1/rooms \
  -H 'Content-Type: application/json' \
  -d "{
    \"hotelId\":\"$HOTEL_ID\",
    \"roomTypeId\":\"$ROOM_TYPE_ID\",
    \"floor\":3,
    \"number\":\"301\"
  }"
```

**응답 (201)**:
```json
{
  "data": {
    "id": "...",
    "hotelId": "...",
    "roomTypeId": "...",
    "floor": 3,
    "number": "301",
    "status": "ACTIVE"
  }
}
```

**내부 동작 (트랜잭션 경계)**:
1. Hotel 존재 검증
2. RoomType 존재 검증 + `roomType.hotelId()` 과 요청 `hotelId` 일치 검증 (**cross-hotel RoomType 사용 차단**)
3. `existsByHotelIdAndFloorAndNumber(hotelId, floor, number)` 중복 검증
4. `Room.create(...)` → `status = ACTIVE`
5. `roomRepository.save(room)` + **`outboxEventPublisher.publish(new RoomCreatedEvent(...), "hotel-events", hotelId)`** 를 **같은 트랜잭션** 에서 실행
6. Trans 커밋 후 `OutboxRelay` 가 비동기로 Kafka 전송

**발행되는 이벤트 (Kafka payload JSON)**:
```json
{
  "eventId": "01970000-...",
  "occurredAt": "2026-04-23T10:00:00Z",
  "hotelId": "01970000-...",
  "roomId": "01970000-...",
  "roomTypeId": "01970000-..."
}
```
- Topic: `hotel-events`
- Partition Key: `hotelId` (동일 호텔 이벤트 순서 보장 — Redis Read Model 일관성)

**주요 에러**:
| 상황 | Status | code |
|---|---|---|
| hotelId 존재하지 않음 | 404 | HOTEL_NOT_FOUND |
| roomTypeId 존재하지 않음 | 404 | ROOM_TYPE_NOT_FOUND |
| RoomType 이 다른 호텔 소속 | 409 | ROOM_TYPE_HOTEL_MISMATCH |
| 동일 (hotel, floor, number) 중복 | 409 | DUPLICATE_ROOM_NUMBER |
| `floor`, `number` 제약 위반 | 400 | VALIDATION_FAILED |

**학습 포인트**: **ROOM_TYPE_HOTEL_MISMATCH** 는 "A 호텔의 B 타입 RoomType 을 C 호텔의 객실에 지정" 하려는 참조 오류를 차단. 같은 hotel-service 안에 있으므로 **FK 대신 도메인 검증** 으로 해결. rate-service 는 같은 성질의 검증을 *하지 못한다* (서비스 경계 너머이므로) → [001 학습 노트 §4-1](./001-hotel-rate-service-split-analysis.md) 참조.

---

### 2-7. 객실 조회 — GET /api/v1/rooms/{roomId}
단건 조회.

### 2-8. 객실 목록 — GET /api/v1/rooms?hotelId=
호텔 내 객실 전체 (soft-delete 포함).

---

### 2-9. 객실 타입 변경 — PATCH /api/v1/rooms/{roomId} ⭐ 이벤트 발행 O

**시나리오**: 객실을 Standard → Deluxe 로 업그레이드 / 재분류. Inventory 집계 기준이 바뀌므로 이벤트로 reservation-service 에 알린다.

**요청**:
```bash
curl -X PATCH http://127.0.0.1:8081/api/v1/rooms/$ROOM_ID \
  -H 'Content-Type: application/json' \
  -d "{\"roomTypeId\":\"$NEW_ROOM_TYPE_ID\"}"
```

**응답 (200)**: 갱신된 Room 표현.

**내부 동작**:
1. Room 조회 (없으면 404)
2. 새 RoomType 조회 + 같은 호텔 소속 검증
3. `room.reassignRoomType(newRoomTypeId, clock)` → `updatedAt` 갱신
4. save + `RoomUpdatedEvent` outbox 적재 (topic=`hotel-events`, key=`hotelId`)

**이벤트 의미**: reservation-service 가 "이 객실의 Inventory 소속 bucket 을 이전 타입 → 새 타입으로 이동" 처리 (후속 PR).

---

### 2-10. 객실 soft-delete — DELETE /api/v1/rooms/{roomId} ⭐ 이벤트 발행 O + 멱등

**시나리오**: 물리적으로 폐쇄된 객실을 시스템에서 폐지. 단 **과거 예약 이력을 보존** 해야 하므로 물리 DELETE 가 아닌 `status = DEACTIVATED` 로 상태 전이.

**요청**:
```bash
curl -X DELETE http://127.0.0.1:8081/api/v1/rooms/$ROOM_ID
```

**응답 (200)**:
```json
{ "data": null }
```

**내부 동작 (멱등성 포인트)**:
1. Room 조회
2. **이미 DEACTIVATED 면** → save / 이벤트 발행 모두 skip, 200 반환 (멱등)
3. 그렇지 않으면 `status = DEACTIVATED` 전이 + `RoomDeletedEvent` 발행

**학습 포인트**:
- **DELETE 가 204 대신 200 + `data=null`** 로 설계됨 — CLAUDE.md 서비스 구현 규약.
- **멱등성**: 같은 DELETE 를 여러 번 호출해도 이벤트가 2번 발행되지 않는다. 네트워크 재시도 · 뱃지 처리에 안전.

**Room 상태 전이 규칙** (Aggregate 가 강제):
```
ACTIVE ⇄ UNDER_MAINTENANCE → DEACTIVATED (단방향)
```
- `DEACTIVATED` 에서 ACTIVE 로 돌아가는 경로 **없음** (재사용 필요 시 신규 Room 등록).
- 현재 PR 에 `UNDER_MAINTENANCE` 전이 API 는 미구현.

---

## 3. rate-service (Port 8082)

**Bounded Context**: 객실 타입 · 날짜별 요금 정책
**Aggregate 1개**: `RoomTypeRate`
**DB**: MySQL `rate` 스키마 (rate-db:3308)

### 📋 API 요약표

| # | API | 메서드 | 경로 | 이벤트 발행 | 핵심 검증 |
|---|---|---|---|---|---|
| 3-1 | 요금 등록 | POST | `/api/v1/room-type-rates` | **❌** (FR-R-03 해석) | 자연키 UNIQUE, amount ≥ 0, ISO-4217 |
| 3-2 | 요금 변경 | PATCH | `/api/v1/room-type-rates/{rateId}` | `RoomTypeRateChangedEvent` (변경 시만) | 존재, 동일 금액 no-op |
| 3-3 | 요금 범위 조회 | GET | `/api/v1/room-type-rates?hotelId=&roomTypeId=&from=&to=` | — | `from ≤ to`, 범위 ≤ 366일 |

**중요 결정**: rate-service 는 hotel-service 의 hotelId / roomTypeId **존재성을 검증하지 않는다**. Database per Service 원칙상 크로스-서비스 동기 호출은 최소화. 고아 레코드 정리는 후속 PR 에서 이벤트 구독으로 수행 예정 ([001 학습 노트 §4-1](./001-hotel-rate-service-split-analysis.md)).

---

### 3-1. 요금 등록 — POST /api/v1/room-type-rates

**비즈니스 시나리오**: Revenue Management 팀이 2026-06-01 에 대한 Deluxe 타입의 정가를 등록. 성수기/비수기 · 프로모션 일자 · 주중/주말 차별화를 날짜 단위로 수행.

**요청**:
```bash
curl -X POST http://127.0.0.1:8082/api/v1/room-type-rates \
  -H 'Content-Type: application/json' \
  -d "{
    \"hotelId\":\"$HOTEL_ID\",
    \"roomTypeId\":\"$ROOM_TYPE_ID\",
    \"date\":\"2026-06-01\",
    \"amount\":150000,
    \"currency\":\"KRW\"
  }"
```

**응답 (201)**:
```json
{
  "data": {
    "id": "01970000-...",
    "hotelId": "01970000-...",
    "roomTypeId": "01970000-...",
    "date": "2026-06-01",
    "amount": 150000,
    "currency": "KRW"
  }
}
```

**내부 동작**:
1. `HotelId.of(...)` · `RoomTypeId.of(...)` VO 변환 (UUID 형식 검증)
2. `Money.of(150000, "KRW")` → `java.util.Currency.getInstance("KRW")` 위임 + `amount ≥ 0` 검증
3. `repository.existsByNaturalKey(...)` 자연키 중복 선제 검증
4. `RoomTypeRate.create(...)` → 새 `RateId` (surrogate UUIDv7)
5. save
6. **이벤트 발행 없음** — PRD FR-R-03 은 "요금 **변경** 시 발행" 으로 한정. 등록 시점은 구독자에게 의미가 다르므로 (향후 필요 시 `RoomTypeRateRegisteredEvent` 를 별도 계약으로 추가)

**주요 에러**:
| 상황 | Status | code |
|---|---|---|
| 자연키 중복 | 409 | DUPLICATE_RATE |
| `amount: -1` | 400 | VALIDATION_FAILED (Money VO) |
| `currency: "ZZZ"` (무효 ISO-4217) | 400 | VALIDATION_FAILED (`Currency.getInstance` throws) |
| `hotelId` UUID 형식이 아님 | 400 | VALIDATION_FAILED |
| `date: "2026-02-30"` (파싱 실패) | 400 | Spring 기본 400 (ExceptionHandler 미커버 영역) |

**학습 포인트**:
- **`rateId` 는 surrogate** — 공개 API 에서 `GET /{rateId}` 는 **제공하지 않는다**. 등록 응답에서 받은 뒤 관리자 플로우에서 PATCH 에만 사용.
- **등록 시 이벤트 없음** 은 ddd-architect 검토 결론 — `RoomTypeRateChangedEvent` 를 "등록도 변경이다" 식으로 재사용하면 도메인 언어 왜곡.

---

### 3-2. 요금 변경 — PATCH /api/v1/room-type-rates/{rateId} ⭐ 이벤트 발행 O (조건부)

**시나리오**: 등록된 요금을 프로모션 기간 종료 후 정상가로 되돌리거나, 시장 가격에 맞춰 상향/하향 조정. 변경 시점에 `RoomTypeRateChangedEvent` 를 발행해 billing / 정산 서비스가 구독.

**요청**:
```bash
curl -X PATCH http://127.0.0.1:8082/api/v1/room-type-rates/$RATE_ID \
  -H 'Content-Type: application/json' \
  -d '{"amount":180000,"currency":"KRW"}'
```

**응답 (200)**: 변경된 Rate 표현.

**내부 동작 (no-op 분기가 핵심)**:
1. `repository.findById(rateId)` → 없으면 404
2. `rate.changeAmount(newMoney, clock)` → **boolean 반환**
   - 동일 금액/통화 → `false` (no-op)
   - 다른 금액 → `true`, `updatedAt` 갱신
3. **`false` 일 때**: save · 이벤트 발행 **모두 skip**. DB write 0, Kafka 메시지 0.
4. **`true` 일 때**: `save` + `RoomTypeRateChangedEvent` outbox 적재 (topic=`rate-events`, key=`hotelId`)

**발행 이벤트 payload**:
```json
{
  "eventId": "...",
  "occurredAt": "2026-04-23T10:00:00Z",
  "hotelId": "...",
  "roomTypeId": "...",
  "date": "2026-06-01",
  "amount": 180000,
  "currency": "KRW"
}
```

**주요 에러**:
| 상황 | Status | code |
|---|---|---|
| rateId 미존재 | 404 | RATE_NOT_FOUND |
| amount ≤ 0 | 400 | VALIDATION_FAILED |
| 무효 currency | 400 | VALIDATION_FAILED |

**학습 포인트**:
- **"no-op 은 도메인 지식, 이벤트 발행은 Application 관심사"** 분리 원칙. `changeAmount` 의 boolean 반환이 이 계약.
- 동일 값 PATCH 를 여러 번 호출해도 Kafka 가 조용한지 직접 로컬에서 확인해볼 것 (consumer 에 메시지가 안 찍혀야 정상).

---

### 3-3. 요금 범위 조회 — GET /api/v1/room-type-rates?hotelId=&roomTypeId=&from=&to=

**시나리오**: 관리자 UI 에서 "6월 1일 ~ 6월 30일 Deluxe 요금 표" 를 표시. 또는 예약 서비스가 견적 계산용으로 특정 날짜 구간을 한 번에 조회.

**요청**:
```bash
curl "http://127.0.0.1:8082/api/v1/room-type-rates?hotelId=$HOTEL_ID&roomTypeId=$ROOM_TYPE_ID&from=2026-06-01&to=2026-06-07"
```

**응답 (200)**:
```json
{
  "data": [
    { "id":"...", "hotelId":"...", "roomTypeId":"...", "date":"2026-06-01", "amount":150000, "currency":"KRW" },
    { "id":"...", "hotelId":"...", "roomTypeId":"...", "date":"2026-06-02", "amount":170000, "currency":"KRW" }
  ]
}
```

**내부 동작**:
1. from / to 파싱 (Spring `@DateTimeFormat(iso=DATE)`)
2. `from > to` → 400
3. `ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS (366)` → 400 (DoS 방어)
4. `repository.findByRange(...)` → `rate_date ASC` 정렬

**주요 에러**:
| 상황 | Status | code |
|---|---|---|
| `from > to` | 400 | VALIDATION_FAILED |
| 범위가 366일 초과 | 400 | VALIDATION_FAILED (`"range too wide"`) |
| 필수 파라미터 누락 | 400 | Spring 기본 |

**학습 포인트**:
- 범위 상한이 없으면 악의 요청 하나로 전체 rate-service 의 DB 커넥션과 메모리 점유 가능 → **공개 엔드포인트의 암묵적 DoS 벡터**. code-reviewer 지적 반영.
- 정렬 순서는 `rate_date ASC` 로 고정 — 프론트에서 별도 정렬 불필요.

---

## 4. 종단간 시나리오 예시 (End-to-End)

### 4-1. 신규 호텔 운영 개시 시퀀스

실제로 관리자가 "새 호텔을 오픈" 할 때 시스템에 입력하는 순서:

```
1. POST /api/v1/hotels                   → HOTEL_ID
2. POST /api/v1/room-types (×N)          → ROOM_TYPE_ID[1..N]    (Standard, Deluxe, Suite)
3. POST /api/v1/rooms (×M per type)      → ROOM_ID[...]          + RoomCreatedEvent (×M)
4. POST /api/v1/room-type-rates (×날짜×타입)                       (rate-service)
```

**이벤트 흐름**:
- 3번에서 `hotel-events` 에 `RoomCreatedEvent` 가 **객실 수만큼** 발행됨
- 향후 reservation-service 가 이를 구독해 `RoomTypeInventory` 레코드 생성 (FR-RSV-04)
- rate-service 는 호텔/객실 존재와 무관하게 요금만 등록 (Database per Service)

### 4-2. 시즌 요금 변경 캠페인

```
for date in 2026-07-01..2026-08-31 do
  PATCH /api/v1/room-type-rates/{rateId}  # 성수기 인상
done
```
- **날짜당 1건**의 `RoomTypeRateChangedEvent` 가 `rate-events` 토픽에 발행됨
- 향후 reservation-service 가 이 이벤트로 견적 재계산 또는 billing 갱신 트리거

### 4-3. 운영 중 객실 폐쇄

```
DELETE /api/v1/rooms/{roomId}  → RoomDeletedEvent
```
- reservation-service 가 구독 후 Inventory 제거 (FR-RSV-04)
- rate-service 는 구독하지 않음 — 고아 rate 는 남지만 후속 PR 의 cleanup 배치 책임

---

## 5. 로컬 검증 체크리스트 (테스트 케이스 기반)

각 API 를 로컬에서 수동 검증할 때의 체크리스트. 단위/통합 테스트에서 이미 커버된 것도 **직접 눈으로 확인** 하면 학습에 크게 도움된다.

### hotel-service
- [ ] 호텔 등록 201 + `data.id` 가 UUID v7 형식 (14번째 글자가 `7`)
- [ ] `starRating=6` → 400
- [ ] 같은 호텔에 같은 RoomType name 재등록 → 409 DUPLICATE_ROOM_TYPE_NAME
- [ ] Room 등록 후 `kafka-console-consumer --topic hotel-events` 로 `RoomCreatedEvent` 확인
- [ ] Room PATCH 로 RoomType 변경 시 `RoomUpdatedEvent` 발행 확인
- [ ] Room DELETE 2회 호출 → 첫 번째만 이벤트 발행, 두 번째는 멱등 (이벤트 없음)
- [ ] A 호텔의 RoomType 으로 B 호텔의 Room 생성 시도 → 409 ROOM_TYPE_HOTEL_MISMATCH
- [ ] hotel DB 에서 `SELECT HEX(id), topic, event_type, published_at FROM hotel_outbox` 로 outbox 행과 published_at 시각 확인

### rate-service
- [ ] 요금 등록 201 + `rate-events` 토픽은 조용 (등록 시 이벤트 없음)
- [ ] 동일 자연키 재등록 → 409 DUPLICATE_RATE
- [ ] 요금 변경 (다른 값) → `RoomTypeRateChangedEvent` 발행 확인
- [ ] 동일 값으로 재 PATCH → 이벤트 없음 (no-op)
- [ ] `amount=-1` → 400 VALIDATION_FAILED
- [ ] `currency="ZZZ"` → 400 VALIDATION_FAILED
- [ ] `from=2026-06-02&to=2026-06-01` → 400 "from must be <= to"
- [ ] `from=2026-01-01&to=2027-06-01` (500일) → 400 "range too wide"
- [ ] rate DB outbox 행 확인

---

## 6. 다음 단계에서 추가될 API (예고)

### guest-service (PR-1.3 예정)
- POST /api/v1/guests — 투숙객 등록 (FR-G-01)
- GET /api/v1/guests/{guestId}
- PATCH /api/v1/guests/{guestId}
- **gRPC** `GetGuest` · `BatchGetGuests` (reservation-service 가 호출) — 이 프로젝트 **최초의 gRPC 서버**

### reservation-service (PR-2.x 예정)
- `hotel-events` 구독 → Inventory 레코드 생성/제거
- POST /api/v1/reservations — 예약 생성 (gRPC `GetGuest` + `GetRoomTypeRate` + 로컬 트랜잭션)
- DELETE /api/v1/reservations/{id} — 예약 취소 + 재고 복원

### hotel-service Phase 3 (PR-3.x 예정)
- GET /api/v1/availability — Redis 기반 가용성 조회 (CQRS Read Model)
- `reservation-events` 구독 → Redis 캐시 갱신
- 캐시 재구축 배치 (reservation-service `StreamInventory` gRPC 호출)

---

## 7. 참고

- [PRD §5 기능 요구사항](../prd/hotel_reservation_prd.md)
- [001 학습 노트 — 서비스 분리 결정 분석](./001-hotel-rate-service-split-analysis.md)
- [ADR 0003 Saga for Reservation](../adr/0003-saga-for-reservation.md)
- [ADR 0004 CQRS for RoomAvailability](../adr/0004-cqrs-for-room-availability.md)
- CLAUDE.md — 서비스 구현 규약 (CommonResponse · Lombok · Outbox · ErrorResponse)

---

**마지막 업데이트**: 2026-04-23

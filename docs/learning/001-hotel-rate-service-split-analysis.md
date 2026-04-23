# 학습 노트: hotel-service ↔ rate-service 분리 결정 분석

> **Status**: Learning Note (ADR/PRD 아님 — 자기 학습용 회고)
> **작성일**: 2026-04-23
> **대상 결정**: PRD §3.1 에 명시된 hotel-service 와 rate-service 의 서비스 분리
> **참고**: [PRD](../prd/hotel_reservation_prd.md) · [ADR 0003](../adr/0003-saga-for-reservation.md) · [ADR 0004](../adr/0004-cqrs-for-room-availability.md)

---

## 0. 학습 목표

MSA 설계에서 **서비스 경계를 긋는 결정** 은 돌이키기 어렵다. 이 문서는 본 프로젝트의 `hotel-service` 와 `rate-service` 분리 결정을 **면접관 / 아키텍처 리뷰어 관점**에서 비판적으로 재검토해, 다음을 체화하는 것이 목표다.

1. "왜 분리했는가" 의 **근거** 를 언어화할 수 있다.
2. 분리에서 지불하는 **비용** 을 정량적으로 나열할 수 있다.
3. "언제 분리하고 언제 합쳐야 하는가" 의 **판단 기준** 을 스스로 세울 수 있다.
4. 면접 follow-up 질문에 방어 논리를 준비할 수 있다.

---

## 1. 맥락 복습

### 현재 결정
- `hotel-service`: 호텔 · 객실 타입 · 객실 마스터 데이터 CRUD + Redis Read Model
- `rate-service`: `(호텔, 객실타입, 날짜)` 별 요금 정책 CRUD
- **별도 서비스 + 별도 MySQL 스키마**
- 상호 공유는 `contracts` 모듈의 gRPC proto / Kafka 이벤트만

### 결정의 흐름
1. 초기 계획(plan 문서)에서는 **단일 서비스 DDD Layered** 가 후보였음
2. PRD v2 개정에서 **MSA (4 서비스)** 로 전환
3. 서비스 경계는 **Bounded Context 단위** 로 그음 (Evans · Vernon 지지)

---

## 2. 핵심 질문

> **"hotel-service 와 rate-service 를 따로 둔 이유가 합리적인가?"**

이 질문은 사실 아래 3개 하위 질문으로 분해해야 제대로 답할 수 있다.

| Q# | 질문 |
|---|---|
| Q1 | `Hotel` 과 `RoomTypeRate` 를 **별도 Aggregate** 로 볼 근거가 있는가? |
| Q2 | 별도 Aggregate 라면, 반드시 **별도 서비스** 여야 하는가? (같은 모놀리스 안 다른 모듈도 가능) |
| Q3 | 별도 서비스라면, 그 **비용을 지불할 만한 실증적 신호** 가 존재하는가? |

세 질문 각각에 대한 답을 구분해 쓰지 않으면 논의가 엉키기 쉽다.

---

## 3. 분리 찬성 근거 (Pros)

### 3-1. 변경 주기 · 쓰기 패턴의 차이 (⭐)
- **Hotel 마스터**: 호텔 오픈 · 리모델링 · 시설 변경 시에만 UPDATE. 월~연 단위.
- **Rate**: 시즌성 요금 / 프로모션 / 경쟁사 가격 추적(Rate Shopper). 하루 수천~수만 건 upsert 가능.
- 한 DB 에 섞이면 **요금 대량 배치 트랜잭션이 호텔 마스터 조회 커넥션 풀을 고갈** 시킬 수 있다.
- 쓰기 패턴 차이가 ≥10× 이면 DB 분리의 고전적 정당화.

### 3-2. 조직 · 팀 경계 (Conway's Law)
- 실제 호텔 체인 운영: **프로퍼티 운영팀 ≠ Revenue Management 팀**.
- 서비스 경계가 팀 경계와 일치하면:
  - 배포 리듬 독립
  - 코드 소유권 명확
  - 커뮤니케이션 오버헤드 감소

### 3-3. 데이터 볼륨 · 수명
- Hotel: `#호텔 × #객실타입 × #객실` — 수만~수십만 row
- Rate: `#호텔 × #객실타입 × #날짜` — **2년치만 해도 한 자릿수 배 이상 큼**
- 인덱스 · 샤딩 · 파티셔닝 전략이 근본적으로 다를 수 있음

### 3-4. DDD Bounded Context 경계 명확
- Ubiquitous Language 가 겹치지 않는다.
  - Hotel: 편의시설 / 등급 / 주소
  - Rate: 금액 / 통화 / 유효 날짜 / 정산
- VO · 불변식이 공유되지 않는다.
- Evans §14: **"Linguistic boundary = Context boundary"** 기준 만족.

### 3-5. 결정의 추적성
- PRD §3.1 에 명시된 책임 분배
- ADR 0003 / 0004 에 Saga · CQRS 근거 문서화
- 나중에 돌아봐도 "왜 그랬는지" 가 남아 있다 (리버스 엔지니어링 비용 ↓)

---

## 4. 분리 반대 근거 (Cons) — **면접관이 공격할 구간**

### 4-1. 참조 무결성 부재 (⭐ 핵심 공격 지점)
- `rate-service` 의 `HotelId` · `RoomTypeId` 는 **존재성을 검증하지 않는다**.
- 현재 PR 주석: *"존재성 검증은 후속 PR 에서 이벤트 구독으로"*
- 단일 서비스였다면 **FK 제약** 한 줄이면 해결.

**위험 시나리오**:
1. 존재하지 않는 `hotelId` 로 요금 등록 → 고아 레코드 (주문 접수되는 호텔이 없음)
2. Hotel 삭제(soft-delete) → `RoomDeletedEvent` 가 rate-service 까지 전파 지연 → 삭제된 객실 타입에 요금 UPDATE
3. 이벤트 유실 시 **영구 불일치** → 수동 배치 정리 필요

### 4-2. 예약 생성 시 N+1 gRPC (성능 함정)
- `reservation-service` 의 예약 생성: 체크인~체크아웃 **매 날짜마다** `rate.proto/GetRoomTypeRate` 를 호출 (proto 주석에 명시).
- 3박 예약 → gRPC 3회 + Circuit Breaker Deadline 3초씩
- 단일 서비스였다면 **SQL 한 번** (`SELECT SUM(amount) ... BETWEEN ...`)

**수치 비교 (추정)**:
| 방식 | 단계 | 총 지연 p99 |
|---|---|---|
| 단일 서비스 + SQL SUM | DB 왕복 1회 | ~10ms |
| MSA + gRPC N 회 (3박) | gRPC 3 × ~20ms + 각 서비스 DB | ~80~150ms |
| MSA + Batch gRPC | gRPC 1 × ~30ms | ~40ms |

**→ Batch RPC (`BatchGetRoomTypeRates`) 설계 누락은 분리의 비용이 설계에 반영되지 않은 증거.**

### 4-3. 조회 경로 Fan-out
- 프론트 "호텔 상세 + 6월 요금" 페이지 = 서비스 2개 호출
- BFF (Backend-for-Frontend) 또는 Gateway aggregation 필요
- **현재 프로젝트에 BFF 는 없다** → 분리의 대가를 아직 지불 안 함

### 4-4. 운영 복잡도 2배 (실측 비용)
PR-1.2 한 건만으로도:
- Flyway 마이그레이션 × 2 세트
- Outbox 테이블 · Repository · Entity × 2 (코드 거의 복제)
- Docker-compose MySQL 컨테이너 × 2 → 로컬 RAM +800MB
- ArchUnit 규칙 · 통합 테스트 세트 × 2

→ **"Database per Service" 의 교조화 증상**.

### 4-5. 독립 배포 · 독립 확장의 실효성 미검증
분리의 **최대 이익은 독립 배포/확장**. 그런데:
- 학습/초기 단계는 모든 서비스를 **같이 배포**
- 트래픽 분포가 미지수 → **스케일 차별화 이익 정량화 불가**
- *"미래에 그럴 것이다"* 는 **YAGNI 의 전형적 위반**

> Sam Newman (*Building Microservices*, 2nd ed.) 경고:
> **"Start with a monolith. Split when you have evidence of real cost."**

---

## 5. 평가 매트릭스

| 축 | 분리 이득 | 분리 비용 | 본 프로젝트 실효성 |
|---|---|---|---|
| 변경 주기 분리 | 높음 | 낮음 | ✅ 유효 |
| 쓰기 볼륨 분리 | 높음 | 낮음 | ⚠️ 가정 (실측 전) |
| 독립 배포 | 매우 높음 | 중간 (CI/CD) | ⚠️ 학습용이라 미활용 |
| 독립 확장 | 높음 | 중간 (K8s) | ⚠️ 현재 무의미 |
| DDD BC 경계 | 중간 | 낮음 | ✅ 유효 |
| 참조 무결성 | — | **매우 높음** | ❌ 현재 미해결 |
| N번 gRPC (예약 생성) | — | **높음 (p99 위협)** | ❌ Batch RPC 미설계 |
| 운영 복잡도 | — | **중간** | ❌ 이미 지불 중 |
| 학습 효과 | — | 낮음 | ✅ 학습 목적에는 이익 최대 |

**한 줄 요약**: 구현은 정석대로 해냈지만, **분리의 비용을 내가 실제로 감당할 수 있는가** 에 대한 정량 답이 약한 것이 유일한 약점.

---

## 6. 면접 Q&A 세트

### 6-1. 🟢 기본 (필수 방어)

**Q1. 왜 요금을 Hotel Aggregate 의 일부로 보지 않고 별도 Aggregate 로 분리했나?**

→ Aggregate 경계는 **트랜잭션 일관성 경계**다. 요금 변경이 호텔 변경의 원자성과 묶일 필연이 없다. 오히려 묶으면:
- 한 호텔의 1일치 요금 변경이 호텔 AR 전체의 `version` 을 흔들어 낙관적 잠금 충돌 빈도 ↑
- AR 메모리 크기 증가 (2년치 요금 = 수천 row)

---

**Q2. 왜 별도 Aggregate 를 같은 서비스가 아니라 다른 서비스에 둬야 하는가?**

→ Bounded Context ≠ Service. 별도 Aggregate 라도 같은 서비스 내 다른 모듈로 둘 수 있다. 별도 **서비스** 로 가려면 추가 근거가 필요:
1. 쓰기/읽기 패턴의 크기 차이
2. 조직/팀 분리
3. 배포 · 스케일 독립성 요구

본 프로젝트는 **1· 2** 는 PRD 수준에서 정당화되나, **3** 은 학습 단계라 실증이 아직 없다.

---

### 6-2. 🟡 중급 (지적 답변 가능해야 가산점)

**Q3. rate-service 가 hotelId 존재 검증을 안 하는 지금 구조의 위험을 설명하고, 어떻게 완화할지 3가지 제안해보라.**

→
1. **이벤트 구독 기반 로컬 캐시**: `HotelCreated`/`RoomTypeRegistered` 를 구독해 rate-service 가 **자체 hotel_ref 테이블** 을 유지. 쓰기 시 JOIN 으로 FK 대체.
2. **Saga 기반 보상**: 존재하지 않는 hotelId 로 요금이 등록되면 후속 검증 배치가 해당 레코드를 soft-delete + admin 알림.
3. **gRPC `ExistsHotel` 동기 조회 (최후 수단)**: Deadline 200ms + Circuit Breaker. 동기 결합을 감수하므로 권장 안 함. 하지만 데이터 품질 SLA 가 엄격하면 선택 가능.

---

**Q4. N번 gRPC 호출을 Batch RPC 로 묶으면 무엇이 바뀌나?**

→
- 지연: N × RTT → 1 × RTT
- Circuit Breaker 통계도 안정화 (N번 중 1번 실패 시 전체 실패로 카운트 vs 1번 호출 1번 카운트)
- proto 변경: `BatchGetRoomTypeRates(BatchGetRoomTypeRatesRequest)` 추가. Request 는 `(hotel_id, room_type_id, repeated date)` 또는 `(hotel_id, room_type_id, from, to)`.
- 비용: rate-service 쪽에서 대용량 요청 DoS 방어 필요 (범위 상한 — 본 PR 의 366일 상한과 같은 아이디어).

---

**Q5. 하나의 서비스였다면 지금 지불 중인 비용 중 무엇이 없어졌을지 3가지 들어보라.**

→
1. **Outbox 인프라 중복** (테이블 · Repository · Relay 스케줄러 × 2 → × 1)
2. **이벤트 멱등성 처리** (`processed_events` 테이블 및 멱등 키 관리)
3. **gRPC 계약 · 서버 · 클라이언트 · Circuit Breaker · Deadline 튜닝**

---

### 6-3. 🔴 심화 (대답하면 아키텍트 레벨 인정)

**Q6. 만약 모놀리스로 시작했다면, rate 를 어떤 시점에 · 어떤 신호를 보고 분리할 것인가?**

→ **정량 기준**:
| 신호 | 임계 |
|---|---|
| 요금 배치 시 DB 락 대기 시간 p99 | > 500ms |
| Hotel 조회 QPS vs Rate 조회 QPS | 10× 차이 |
| 배포 주기 충돌 | 월 2회 이상 Hotel 팀 vs Rate 팀 코드 충돌 PR |
| 팀 크기 | Rate 전담 엔지니어 ≥ 3명 |
| DB 테이블 크기 | Rate 테이블 > Hotel 테이블 100× |

**정성 기준**:
- Revenue Management 팀이 독립 조직으로 신설
- Rate 관련 사이클(프로모션 엔진 · ML 가격 최적화)이 독립 로드맵을 가짐

---

**Q7. reservation-service 가 RoomTypeInventory (SoT) 를 소유한 결정과, rate 를 분리한 결정은 같은 논리인가?**

→ **다르다**.
- **Inventory SoT**: 예약 생성 트랜잭션과 같은 로컬 트랜잭션에 묶여야 **오버부킹 방지**. 트랜잭션 경계가 필연적.
- **Rate**: 예약 생성과 같은 트랜잭션일 필요 없음. 조회만 하면 됨. 즉 **트랜잭션 강제성이 없음**.

→ Inventory 는 서비스 경계가 트랜잭션에 의해 결정됨. Rate 는 서비스 경계가 **선택의 문제** (조직 · 쓰기 패턴 · 확장성).

**이 차이를 인식하느냐** 가 Aggregate 경계 감각의 핵심.

---

**Q8. 분리로 얻은 독립 확장성을 이 코드베이스에서 실제 보여줄 수 있나?**

→ 현재는 불가능. 솔직히 인정한 뒤:
> *"측정 포인트는 준비돼 있다. (a) rate-events 구독 지연, (b) GetRoomTypeRate gRPC p99, (c) rate-service 배포 빈도 대비 hotel-service 배포 빈도. Phase 4 관측 도입 이후 6개월간 측정해 임계를 넘지 않으면 재통합도 고려할 수 있다."*

→ **"측정할 계획이 있다" 는 답변이 "독립 확장 가능합니다!" 보다 훨씬 고평가**.

---

## 7. 스스로 체크리스트 (자기 평가)

다음을 **말로 설명할 수 있으면** 이 주제는 졸업.

- [ ] Bounded Context 와 서비스 경계가 **같은 것이 아니라는 것** 을 설명할 수 있다
- [ ] 왜 Aggregate 경계 = 트랜잭션 경계인지 1분 안에 설명할 수 있다
- [ ] Database per Service 의 **비용 3가지** 를 즉석에서 나열할 수 있다
- [ ] N번 gRPC vs SQL JOIN 의 p99 차이를 추정할 수 있다
- [ ] Sam Newman 의 **"monolith first"** 주장을 방어 · 반박 둘 다 해본다
- [ ] 본 프로젝트에서 분리를 정당화할 **정량 기준** 3개를 만들 수 있다
- [ ] rate-service 의 참조 무결성 결함을 구체 시나리오로 설명한다
- [ ] "이 결정을 언제 되돌릴 것인가" 에 대한 철회 조건 (sunset condition) 을 쓸 수 있다

---

## 8. 관련 용어 사전

| 용어 | 정의 | 출처 |
|---|---|---|
| **Bounded Context** | 특정 도메인 모델이 일관된 의미를 갖는 언어적 경계 | Evans, *DDD* §14 |
| **Aggregate** | 트랜잭션 일관성을 보장하는 Entity + VO 의 클러스터, 외부는 Root 를 통해서만 접근 | Evans §6, Vernon *IDDD* Ch.10 |
| **Aggregate Root (AR)** | Aggregate 의 진입점. 외부 참조는 ID 로만 | Evans |
| **Database per Service** | 각 서비스가 자기 DB 를 독점. 공유 금지 | Richardson, *Microservices Patterns* Ch.2 |
| **Saga** | 로컬 트랜잭션들의 보상(compensation) 기반 분산 일관성 패턴 | Richardson Ch.4 |
| **CQRS** | Command / Query 를 물리적으로 분리. 쓰기 모델 ≠ 읽기 모델 | Greg Young, Fowler |
| **Outbox Pattern** | 비즈니스 상태 변경과 메시지 발행을 같은 트랜잭션에 묶는 기법 | Richardson Ch.3 |
| **YAGNI** | "You Aren't Gonna Need It" — 선제적 일반화 금지 | XP, Kent Beck |
| **Conway's Law** | 시스템 구조는 조직 구조를 반영한다 | Conway 1968 |
| **Reverse Conway Maneuver** | 원하는 시스템 구조에 맞춰 팀을 설계 | *Team Topologies* |

---

## 9. 참고 문헌

### 필독
- Eric Evans, *Domain-Driven Design* (2004) — 특히 Part IV "Strategic Design"
- Vaughn Vernon, *Implementing Domain-Driven Design* (2013) — Ch.2 "Domains, Subdomains, Bounded Contexts", Ch.10 "Aggregates"
- Sam Newman, *Building Microservices* 2nd ed. (2021) — Ch.3 "Splitting the Monolith", Ch.1 "What Are Microservices?"

### 권장
- Chris Richardson, *Microservices Patterns* (2018) — Saga · Outbox · CQRS 패턴 구현 레벨
- Matthew Skelton & Manuel Pais, *Team Topologies* (2019) — Conway's Law 현대판

### 아티클
- Martin Fowler, [*MonolithFirst*](https://martinfowler.com/bliki/MonolithFirst.html) — 본 결정의 가장 큰 비판 근거
- Martin Fowler, [*BoundedContext*](https://martinfowler.com/bliki/BoundedContext.html)

### 본 프로젝트 내부 문서
- [`docs/prd/hotel_reservation_prd.md`](../prd/hotel_reservation_prd.md) §3.1 (서비스 책임 분배)
- [`docs/adr/0003-saga-for-reservation.md`](../adr/0003-saga-for-reservation.md)
- [`docs/adr/0004-cqrs-for-room-availability.md`](../adr/0004-cqrs-for-room-availability.md)

---

## 10. 다음 학습 주제

이 문서를 완독한 후 확장할 수 있는 학습 질문들:

1. **Inventory 소유권 결정** (`reservation-service` 가 SoT) 도 같은 방식으로 비판적 재검토
2. **RoomAvailabilityView (Redis CQRS)** 의 최종 일관성 window 가 비즈니스적으로 수용 가능한지 정량 분석
3. **Saga 보상 트랜잭션** 의 실패 시나리오 (보상도 실패하면?) — Dead Letter Queue / 운영 알람 설계
4. **contracts 모듈의 버전 관리** — proto breaking change 시 호환성 전략 (Schema Registry 필요성)
5. **이 프로젝트를 모놀리스 1개로 재구성한다면** 의 사고 실험 — 무엇이 간단해지고 무엇을 포기하는지

---

**마지막 업데이트**: 2026-04-23

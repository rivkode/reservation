# Claude Code 프롬프트 템플릿

호텔 예약 MSA 프로젝트를 Claude Code로 진행할 때 그대로 복사해서 쓸 수 있는 프롬프트 모음.

**전제**: 프로젝트 루트에 `CLAUDE.md`, `.claude/`, `docs/prd/hotel_reservation_prd.md` 가 설치되어 있음.

---

## 🚀 프롬프트 1 — 최초 시작 (PRD 분석 + 계획)

처음 Claude Code에 보내는 프롬프트. **코드는 작성하지 않고 계획만** 나오도록 구성.

```
@docs/prd/hotel_reservation_prd.md

이 PRD 를 기반으로 호텔 예약 MSA 시스템을 구현한다.
CLAUDE.md 의 워크플로우를 엄격히 따른다.

[현재 단계: code-planning Step 1~2 — 계획만, 구현 X]

다음을 보고해줘:

1. **PRD 재진술**
   - Goals / Non-Goals 명시
   - 기술 스택 확인 (Java 21, Spring Boot 3, MySQL, gRPC, Kafka, Redis)
   - 4개 서비스(hotel / rate / guest / reservation) 책임 재확인

2. **재고 소유 구조 이해 확인**
   - 왜 RoomTypeInventory 가 reservation-service 에 있는지
   - 왜 RoomAvailabilityView (Redis) 가 hotel-service 에 있는지
   - 두 개의 이름이 다른 이유

3. **Service Impact 검토**
   - PRD Section 11 의 매트릭스에서 누락되거나 잘못된 매핑이 있는가?

4. **열린 질문 7개에 대한 권장 답안** (PRD Section 13)
   - 각 질문에 대해 업계 관행 기반 추천 + 근거
   - 내가 결정해야 할 것과 기본값으로 진행 가능한 것 구분

5. **PR 분할 검증**
   - PR-0.1 ~ PR-4.3 중 의존 관계 확인
   - 어느 PR 이 먼저 머지되어야 다음이 가능한지
   - 병렬 진행 가능한 조합
   - 가장 먼저 시작할 PR 추천

6. **Phase 0 시작 전 필요한 결정 사항** 목록

**제약**: 코드 한 줄도 쓰지 말 것. 계획 승인 후 PR-0.1 부터 시작한다.
```

---

## 🛠 프롬프트 2 — Phase 0 시작 (멀티모듈 골격)

계획이 승인된 뒤 첫 구현 단계.

```
PRD 의 PR-0.1 (Gradle 멀티모듈 골격) 을 진행해줘.

전체 워크플로우를 따른다:

1. **code-planning** — 구체 계획
   - settings.gradle.kts 에 include 할 모듈
   - 루트 build.gradle.kts 의 공통 설정 (Java 21, 플러그인, 버전 카탈로그 등)
   - 각 서비스 build.gradle.kts 의 책임
   - protobuf-gradle-plugin 은 contracts 모듈에만

2. **module-boundary** 스킬 참조
   - 모듈 의존성 규칙 (서비스 간 project() 참조 금지)
   - contracts, common-infrastructure 외 공유 금지

3. **구현**
   - settings.gradle.kts: 7개 모듈 (contracts, common-infrastructure, hotel-service, rate-service, guest-service, reservation-service)
   - 루트 build.gradle.kts: 공통 설정 (subprojects 블록)
   - 각 서비스 build.gradle.kts: Spring Boot, JPA, MySQL, Kafka, gRPC, Resilience4j, ArchUnit
   - contracts/build.gradle.kts: protobuf-gradle-plugin + Java record

4. **빌드 검증**
   - ./gradlew build 가 성공해야 함 (각 서비스에 최소한의 main 클래스 포함)

5. **code-reviewer 에이전트 호출**
   - 모듈 경계 위반 선결 검사 (서비스간 project 참조 없는지)

6. **커밋 + PR**

각 주요 단계마다 짧게 요약 보고하고 다음으로 넘어가.
중간에 내 결정이 필요한 분기가 나오면 먼저 물어봐.

완료되면 PR-0.2 (contracts 모듈) 로 넘어갈지 확인 요청.
```

---

## 📦 프롬프트 3 — Phase 0 계속 (contracts + common-infrastructure)

```
PR-0.2 (contracts 모듈) 를 진행해줘.

**목표**: 서비스 간 공개 계약을 담는 contracts 모듈 골격 완성.

구현 내용:
- protobuf-gradle-plugin 적용 (proto → Java 생성)
- src/main/proto/ 하위에 4개 proto 파일 골격
  * guest.proto — GetGuest RPC (reservation → guest 호출)
  * reservation.proto — StreamInventory RPC (hotel → reservation 호출, 캐시 재구축용)
  * hotel.proto — (골격만, 향후 확장)
  * rate.proto — (골격만)
- src/main/java/com/example/contracts/event/ 하위 이벤트 record 정의
  * hotel/RoomCreated, RoomUpdated, RoomDeleted
  * rate/RoomTypeRateChanged
  * reservation/ReservationCreated, ReservationCancelled
  * billing/BillingCreated, BillingCreationFailed

워크플로우:
1. code-planning: 어떤 proto / 이벤트가 필요한지 PRD Section 7 기준 정리
2. module-boundary 스킬 필수 참조
   - contracts 작성 규칙 (Spring/JPA 어노테이션 금지)
   - 이벤트 record 규칙 (eventId, occurredAt 필수, JavaDoc 명시)
3. 구현 + ArchUnit 규칙 (ContractsArchitectureTest)
4. testing-junit: contracts 는 테스트가 최소 — ArchUnit + 이벤트 record validation 테스트 정도
5. code-reviewer 호출
6. 커밋 + PR

완료 후 PR-0.3 (common-infrastructure), PR-0.4 (ArchUnit 전체 적용), PR-0.5 (Docker Compose) 순서로 진행.
```

---

## 🏨 프롬프트 4 — Phase 1 시작 (hotel-service)

서비스별 기본 CRUD 구현.

```
PR-1.1 (hotel-service — Hotel / Room Aggregate + CRUD API + 이벤트 발행) 을 진행해줘.

이 PR 은 단일 서비스 기능이므로 전체 워크플로우를 완주한다:

1. **code-planning**
   - Hotel, Room 의 책임 분리 (어느 쪽이 Aggregate Root? 둘 다?)
   - 상태 전이 (Room 이 가질 수 있는 상태)
   - 필요한 API 엔드포인트 (CRUD + 검색)
   - 발행할 이벤트 (RoomCreated, RoomUpdated, RoomDeleted)

2. **🤖 ddd-architect 서브 에이전트 호출** (필수 — 새 Aggregate)
   - Hotel / Room 경계 검증
   - 이벤트 설계 검증

3. **module-boundary** — Outbox 테이블 설계 (MySQL)

4. **ddd-architecture 구현** (도메인 → 애플리케이션 → 인프라 → 프레젠테이션 순)
   - domain/model: Hotel, Room, HotelId, RoomId, RoomType, HotelGrade 등 VO
   - domain/repository: HotelRepository, RoomRepository 인터페이스
   - domain/event: 내부 이벤트 (contracts 이벤트로 변환 전)
   - application/service: HotelService, RoomService
   - application/dto: Command / Query / Result
   - infrastructure/persistence: MySQL (JpaEntity + Mapper + RepositoryImpl)
   - infrastructure/messaging: OutboxEventPublisher + Outbox Relay 스케줄러
   - presentation/controller: REST API

5. **testing-junit**
   - Domain 단위 테스트 (Spring 없이)
   - Application 단위 테스트 (Repository Mock)
   - Infrastructure 슬라이스 테스트 (@DataJpaTest + Testcontainers MySQL)
   - Presentation 슬라이스 테스트 (@WebMvcTest)
   - ArchUnit 테스트

6. **🤖 code-reviewer + test-reviewer 병렬 호출**
   - code-reviewer: 서비스 경계, DDD 준수, Outbox 패턴
   - test-reviewer: Mock 남용, 계층 독립성

7. **documentation**
   - OpenAPI 생성
   - JavaDoc (public Application Service)
   - ADR 필요한 결정 있는지 확인

8. **commit-convention** + **pr-guidelines**

각 단계 완료 시 짧게 보고. 설계/구현 분기에서 결정 필요하면 먼저 질문.
완료 후 PR-1.2 (rate-service) 또는 PR-1.3 (guest-service) 로 이동 제안.
```

---

## 🔗 프롬프트 5 — 서비스간 통신 추가 (PR-2.2 예약 생성)

여러 서비스에 걸친 복잡한 기능. **module-boundary 스킬이 핵심**.

```
PR-2.2 (reservation-service — Reservation Aggregate + 예약 생성 API) 를 진행해줘.

이 PR 은 **여러 서비스에 걸친 복잡한 기능**이다:
- reservation-service 내부: Reservation + RoomTypeInventory 차감
- guest-service 동기 호출 (gRPC + Deadline + Circuit Breaker)
- ReservationCreated 이벤트 발행 (Outbox + Kafka)

**전제조건 확인**:
- PR-0.1 ~ PR-0.5 (Phase 0) 완료
- PR-1.3 (guest-service + gRPC 서비스) 완료
- PR-2.1 (RoomTypeInventory Aggregate + hotel-events 구독) 완료

워크플로우:

1. **code-planning**
   - Reservation Aggregate 의 책임과 상태 전이
   - 예약 생성 플로우: gRPC 호출 → 로컬 트랜잭션 (재고 차감 + 예약 생성 + Outbox) → 이벤트 발행
   - 에러 시나리오: guest 없음, 재고 부족, gRPC 장애 각각의 HTTP 응답

2. **module-boundary 스킬 필수**
   - guest-service gRPC 호출 설계 (Deadline 3초, CB, Retry)
   - Domain 인터페이스 GuestLookup 정의
   - Infrastructure 의 GrpcGuestLookup 구현
   - ReservationCreated 이벤트 (이미 contracts 에 정의되어 있음, 재확인)
   - 멱등성 처리 방안

3. **🤖 ddd-architect 호출** (필수 — 새 Aggregate + 서비스간 통신 관여)
   - Reservation / RoomTypeInventory 관계
   - 트랜잭션 경계 (gRPC 호출이 트랜잭션 밖에 있어야 함)

4. **구현**
   - domain/model/reservation: Reservation Aggregate
   - domain/service/GuestLookup (인터페이스)
   - domain/model/guest/GuestSummary (이 서비스 내부용 VO, contracts 와 다를 수 있음)
   - application/service/CreateReservationService
   - application/dto/CreateReservationCommand
   - infrastructure/grpc/client/GrpcGuestLookup (@GrpcClient + Resilience4j)
   - infrastructure/persistence: Reservation JpaEntity + Mapper + RepositoryImpl
   - infrastructure/messaging: 이미 구현된 OutboxEventPublisher 사용
   - presentation/controller: POST /api/v1/reservations

5. **testing-junit**
   - Reservation 도메인 단위 테스트
   - CreateReservationService 단위 테스트 (Repository + GuestLookup Mock, Clock.fixed)
   - GrpcGuestLookup 테스트 (gRPC 인메모리 서버 or WireMock + @CircuitBreaker 동작 검증)
   - 예약 생성 API 통합 테스트 (Testcontainers: MySQL + Kafka)

6. **🤖 code-reviewer + test-reviewer 병렬**
   - **특히 체크**: gRPC Deadline 설정, Domain 에 proto 타입 침투 여부, Outbox 트랜잭션 원자성

7. **🤖 security-reviewer** (예약은 사용자 데이터 다룸 — PII)
   - 투숙객 정보 로깅 여부, 응답 마스킹

8. documentation + commit + PR

각 단계 보고하고 다음으로. 설계 분기는 먼저 질문.
```

---

## 🔄 프롬프트 6 — Phase 3 (Read Model: hotel-service 가용성 캐시)

```
PR-3.1 (hotel-service — Redis 가용성 캐시 + reservation-events 구독) 을 진행해줘.

**이 PR 의 핵심**: Read Model 패턴 적용. hotel-service 가 Redis 로 `RoomAvailabilityView` 를 운영.

**중요 설계 원칙** (module-boundary/references/read-model-sync.md 참조):
- 이름 구분: 쓰기는 reservation-service 의 RoomTypeInventory, 읽기는 hotel-service 의 RoomAvailabilityView
- RoomAvailabilityView 는 **Aggregate 가 아니다**. JPA Entity 도 아니다. 불변식 검증도 없다.
- 예약 확정은 reservation-service 에서 재검증 (이 PR 범위가 아님, 이미 PR-2.2 에서 처리)

워크플로우:
1. code-planning
2. module-boundary 스킬 + read-model-sync.md 참조 필수
3. 구현:
   - infrastructure/cache: RoomAvailabilityCacheUpdater (Redis 쓰기)
   - infrastructure/cache: RoomAvailabilityCacheReader (Redis 읽기)
   - infrastructure/messaging: ReservationCreatedAvailabilityListener, ReservationCancelledAvailabilityListener
     (둘 다 멱등성 처리 — ProcessedEvent 테이블)
4. testing-junit: Testcontainers Redis 로 캐시 동작 검증 + 멱등성 검증
5. code-reviewer + test-reviewer
6. 문서 + 커밋 + PR

완료 후 PR-3.2 (가용성 조회 API) 로 넘어갈지 확인.
```

---

## 🔧 프롬프트 7 — 특정 문제 / 리팩토링

기능 구현 중이 아닌 특정 문제 해결 시.

```
[상황 설명]
예: "PR-2.2 에서 예약 생성 API 의 응답 시간이 p99 2초를 넘어. guest-service gRPC 호출이 병목인 것 같다."

아래 순서로 도와줘:

1. 원인 분석 (추측 말고 실제 측정 기반으로 어떤 로그/메트릭이 필요한지 제안)
2. 해결 방안 2~3가지 제시 (trade-off 포함)
   - 예: Reservation 생성 시 Guest 정보 캐시? 비동기 이벤트 기반 검증으로 전환?
3. 선택된 방안에 대해 CLAUDE.md 워크플로우 전체 실행
4. ADR 작성 (아키텍처적 변화면)

결정 필요한 분기는 먼저 질문.
```

---

## 📝 프롬프트 8 — 진행 현황 업데이트

여러 번의 세션을 거친 뒤 현재 상태 확인.

```
현재 프로젝트 진행 상황을 정리해줘:

1. git log 와 PR 상태를 보고 PRD 의 "진행 현황" 섹션(Section 14) 체크리스트를 업데이트
2. 완료된 PR 번호 + 머지 SHA 기록
3. 현재 작업 중인 PR 과 남은 TODO
4. PRD 의 "열린 질문" 중 해결된 것과 남은 것 구분
5. 다음에 진행할 PR 추천 (의존 관계 고려)

PRD 파일을 직접 업데이트해도 좋음. 업데이트 후 git commit 으로 기록.
```

---

## 💡 팁

### 매번 프롬프트에 포함시키면 좋은 것

1. **PRD 참조**: `@docs/prd/hotel_reservation_prd.md` (첫 메시지 또는 관련 세션 시작 시)
2. **워크플로우 준수 명시**: "CLAUDE.md 의 워크플로우를 엄격히 따른다"
3. **분기 시 질문 요청**: "결정 필요한 분기는 먼저 물어봐"
4. **단계별 보고 요청**: "각 단계 완료마다 짧게 요약 보고"

### 주의할 점

- ❌ "PRD 보고 전부 구현해줘" — 한 번에 너무 큼
- ❌ 서브 에이전트 호출을 명시하지 않음 — Claude가 건너뛸 수 있음
- ❌ "대충 만들어줘" — 원칙이 흐려짐
- ✅ **PR 단위로 쪼개서 순차 진행**
- ✅ **워크플로우 단계 명시** (code-planning → module-boundary → ...)
- ✅ **서브 에이전트 호출 명시**

### 한 세션이 길어질 때

Claude Code 의 컨텍스트 한도를 고려해, **한 PR 이 끝날 때마다 새 세션**으로 넘어가는 것을 권장. 대신 이전 세션에서 생성한 파일/커밋은 git 에 남아있으므로 다음 세션에서 `@docs/prd/...` + 현재 상황 설명만 하면 이어서 진행 가능.

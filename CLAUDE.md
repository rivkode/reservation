# CLAUDE.md

이 저장소에서 Claude Code가 지켜야 할 **최상위 규칙**. 세부 지침은 `.claude/skills/` 와 `.claude/agents/` 에서 계층적으로 제공된다.

---

## 프로젝트

- **스택**: Java 21, Spring Boot 3.x, Gradle (Kotlin DSL) **멀티모듈** (모노레포)
- **아키텍처**: MSA + 서비스별 DDD Layered
- **서비스간 통신**: 동기 **gRPC** (`grpc-spring-boot-starter`), 비동기 **Kafka**
- **DB**: 서비스당 **MySQL** 스키마 분리 (Database per Service). 테스트는 Testcontainers(MySQL)
- **캐시**: Redis (조회용 Read Model 보관 — hotel-service 의 가용성 조회 등)
- **테스트**: JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers

---

## 서비스 구조

각 서비스는 **독립적인 Bounded Context** 이며 자체 Gradle 모듈이다.

| 서비스 모듈 | 책임 | 주요 Aggregate |
|---|---|---|
| `hotel-service` | 호텔/객실 마스터 정보 관리 + **가용성 조회 Read Model (Redis 캐시)** | `Hotel`, `Room` |
| `rate-service` | 객실 타입별 요금 정책 | `RoomTypeRate` |
| `guest-service` | 투숙객 정보 관리 | `Guest` |
| `reservation-service` | 예약 생성/취소 + **객실 재고의 Source of Truth** | `RoomTypeInventory`, `Reservation` |

### 재고 소유 원칙 (중요)

- **`RoomTypeInventory` (쓰기, SoT)**: `reservation-service` 소유.
  예약 생성/취소 시 같은 로컬 트랜잭션에서 차감/복원하여 **오버부킹 방지**.
- **`RoomAvailabilityView` (읽기, 캐시)**: `hotel-service` 소유.
  고속 조회용. reservation-service 의 이벤트를 구독해 Redis 에 비동기 갱신 (최종 일관성).
- **예약 확정은 반드시 reservation-service 에서 재검증**. 캐시만 믿고 예약 완료하지 않는다.

**공유 모듈** (최소한):
- `contracts` — 서비스간 **공개 계약**.
  - `event/` — Kafka 이벤트 스키마 (`record`)
  - `proto/` — **gRPC .proto 파일 및 생성된 stub**
  - 도메인 공유 금지.
- `common-infrastructure` — 공통 Spring 설정 (로깅, 모니터링, 예외 표준, gRPC 공통 인터셉터). 도메인 공유 금지.

---

## 핵심 원칙 (NEVER 위반)

1. **서비스 경계는 Bounded Context.** 한 서비스의 Domain / JpaEntity / Repository 를 다른 서비스가 **절대** 직접 참조하지 않는다. 공유는 `contracts` 의 명시적 계약만.
2. **Database per Service.** 각 서비스는 자기 DB 스키마만 소유. 다른 서비스 DB에 직접 쿼리 금지.
3. **도메인 객체 ≠ DB Entity.** 도메인에 JPA/Spring 어노테이션 금지. JPA는 `infrastructure/persistence/entity/XxxJpaEntity` 에 격리하고 Mapper 로 변환.
4. **의존성은 항상 안쪽으로.** Domain ← Application ← Infrastructure / Presentation. Domain은 어떤 프레임워크에도 의존하지 않는다.
5. **계층별 테스트는 독립적.** Domain 은 Spring 없이, Application 은 Repository Mock 으로, Infrastructure 는 `@DataJpaTest`, Presentation 은 `@WebMvcTest`.
6. **테스트 없는 코드는 머지 금지.** 모든 public 메서드와 분기는 테스트로 커버.
7. **계획 없이 구현 금지.** 요구사항을 받으면 계획을 수립하고 사용자 승인을 받은 뒤 착수한다.
8. **서비스간 동기 호출 최소화.** 기본은 비동기 이벤트. 동기는 조회성에 한해, Circuit Breaker 필수.

---

## 워크플로우

```
[요구사항]
    ↓
[계획]    📘 code-planning
          ↳ 어느 서비스(들)에 영향? 서비스간 통신 필요?
    ↓
[모듈 경계 검토]   📘 module-boundary  (여러 서비스 관여 / 새 이벤트 / 새 API 시)
    ↓
[설계 검증]   🤖 ddd-architect     (새 Aggregate / 모델 큰 변경 시)
    ↓
[구현]    📘 ddd-architecture
    ↓
[테스트]  📘 testing-junit
    ↓
[리뷰]    🤖 code-reviewer  +  🤖 test-reviewer   (병렬)
          🤖 security-reviewer  (인증/결제/PII/파일/관리자 시)
    ↓
[문서]    📘 documentation
    ↓
[커밋]    📘 commit-convention
    ↓
[PR]      📘 pr-guidelines
```

**판정 대응 규칙**: 에이전트가 `NEEDS CHANGES` / `REJECT` / `BLOCK` 을 반환하면 다음 단계로 진행하지 않고 수정 후 같은 에이전트를 재호출한다. 임의 판단으로 건너뛰지 않는다.

---

## PRD 참조 규칙

새로운 기능 요청을 받으면 `code-planning` 스킬의 Step 1 직전에:

1. `docs/prd/` 디렉토리를 먼저 확인한다.
2. 요청과 관련된 PRD 파일을 **전체** 읽는다.
3. 관련 PRD가 있으면:
   - Goals / Non-Goals 를 명시적으로 재진술에 포함
   - **영향 받는 서비스 모듈을 PRD 의 "Service Impact" 섹션에서 확인**
   - Non-Goals 침범, FR 누락은 즉시 지적
4. PRD 규모가 크면 **서비스별 + 기능별** 로 단위 PR 분할 제안 후 승인받고 착수.

---

## 저장소 디렉토리 구조

```
project-root/
├── CLAUDE.md
├── build.gradle.kts                    # root
├── settings.gradle.kts                 # 모듈 include
├── docs/
│   ├── prd/
│   ├── adr/
│   └── architecture/
├── contracts/                          # 서비스간 공개 계약 (공유 모듈)
│   ├── build.gradle.kts
│   ├── src/main/proto/                 # gRPC .proto 정의
│   │   ├── hotel.proto
│   │   ├── rate.proto
│   │   ├── guest.proto
│   │   └── reservation.proto
│   └── src/main/java/com/example/contracts/
│       └── event/                      # Kafka 이벤트 스키마
├── common-infrastructure/              # 공통 Spring 설정 (공유 모듈)
│   └── ...
├── hotel-service/
│   ├── build.gradle.kts
│   └── src/main/java/com/example/hotel/
│       ├── domain/
│       ├── application/
│       ├── infrastructure/
│       └── presentation/
├── rate-service/
├── guest-service/
└── reservation-service/
```

### 서비스 모듈 내부 구조 (공통)

```
<service-name>/
├── build.gradle.kts
└── src/
    ├── main/java/com/example/<service>/
    │   ├── domain/              # 순수 도메인 (프레임워크 의존 금지)
    │   │   ├── model/
    │   │   ├── repository/
    │   │   ├── service/
    │   │   ├── event/
    │   │   └── exception/
    │   ├── application/         # Use Case
    │   │   ├── service/
    │   │   └── dto/
    │   ├── infrastructure/
    │   │   ├── persistence/
    │   │   │   ├── entity/      # XxxJpaEntity (MySQL)
    │   │   │   ├── repository/
    │   │   │   └── mapper/
    │   │   ├── cache/           # Redis (hotel-service의 가용성 뷰 등)
    │   │   ├── messaging/       # Kafka Producer/Consumer
    │   │   ├── grpc/
    │   │   │   ├── server/      # 이 서비스가 노출하는 gRPC 서비스 구현
    │   │   │   └── client/      # 다른 서비스의 gRPC 호출
    │   │   └── config/
    │   └── presentation/        # 외부(클라이언트) HTTP 진입점
    │       ├── controller/
    │       ├── dto/
    │       └── exception/
    └── test/java/...
```

**note**: 외부(클라이언트)용 API 는 `presentation/controller/` 의 REST Controller 로, 서비스간 호출은 `infrastructure/grpc/server/` 의 gRPC 서비스로 제공한다.

---

## 모듈 의존성 규칙

- ✅ 서비스 모듈 → `contracts`, `common-infrastructure`
- ❌ 서비스 모듈 → **다른 서비스 모듈** (직접 의존 금지)
- ❌ `contracts` → 서비스 모듈 (공유 모듈이 서비스를 몰라야 함)
- ❌ 서비스 모듈 → 다른 서비스의 `domain/` / `infrastructure/` 패키지

다른 서비스 데이터가 필요하면:
1. **gRPC 클라이언트 호출** (조회성, 동기) — `infrastructure/grpc/client/` 에 위치
2. **Kafka 이벤트 구독** (변경 전파, 비동기) — `infrastructure/messaging/` 에 위치

상세는 → `.claude/skills/module-boundary/SKILL.md`

---

## 금지 사항

- ❌ **다른 서비스의 DB 에 직접 쿼리 / JOIN**
- ❌ **다른 서비스의 Domain 객체 / JpaEntity 를 import**
- ❌ **`contracts` 외의 경로로 서비스간 DTO 공유**
- ❌ 도메인 객체에 JPA/Spring 어노테이션
- ❌ Controller 에서 Repository 직접 호출 (Application Service 경유)
- ❌ Application Service 가 다른 Application Service 직접 호출
- ❌ 테스트 `@Disabled` / 회피
- ❌ 한 커밋에 여러 서비스 모듈 변경 혼합 (꼭 필요하면 사용자 승인)
- ❌ 사용자 승인 없는 공개 API / 이벤트 스키마 변경 (Breaking Change)
- ❌ 서브 에이전트 리뷰를 건너뛰고 커밋/PR

---

## 작업 규칙

- 작업 시작 시 `git status` 로 브랜치/변경 확인, 새 기능이면 `feature/<service>-<domain>-<action>` 브랜치. 예: `feature/reservation-cancel-api`
- **build.gradle 의존성을 임의로 추가하지 않는다.** 필요 시 사용자에게 제안.
- 기존 코드 스타일·네이밍을 우선 따른다.
- 작업 완료 전 **영향받은 서비스 모듈**을 빌드한다:
  - 단일 서비스: `./gradlew :hotel-service:build`
  - 여러 서비스: `./gradlew build`
- 추측 금지 — 불명확한 부분은 질문한다.

---

## 서비스 구현 규약 (hotel-service PR-1.1b 이후 고정)

모든 서비스 모듈(rate-service · guest-service · reservation-service) 은 아래 규약을 **동일하게** 따른다. hotel-service 에서 재작업 비용을 치른 결정이므로 이후 서비스는 처음부터 맞춘다.

### Lombok — Spring bean 계열
- `@Service` · `@Repository` · `@RestController` · `@RestControllerAdvice` · `@Configuration(bean 주입형)` 는 **`@RequiredArgsConstructor`** 로 생성자를 만든다.
- SLF4J 로거 선언은 **`@Slf4j`** 로 대체. `private static final Logger log = LoggerFactory.getLogger(...)` 를 수동 선언하지 않는다.
- Spring DI 가 비-null 을 보장하므로 생성자 주입 필드에 대한 `Objects.requireNonNull(...)` 은 **붙이지 않는다**. (값 인자 검증은 유지 — 예: `command`, `id` 등 메서드 파라미터)
- **예외 — 수동 생성자가 필요한 경우**:
  - 다수 오버로드 생성자 (`OutboxRelay` 처럼 default 값 분기용)
  - 생성자 내부에서 인자 범위 검증 (예: `if (batchSize <= 0) throw ...`)
  - 이때도 `@Slf4j` 는 적용한다.
- 도메인 계층(Aggregate · VO · Domain Exception) 은 **Lombok 금지**. 불변식 검증 로직과 명시적 팩토리 메서드가 핵심이라 `@RequiredArgsConstructor` 가 오히려 해롭다. `@Getter` 도 붙이지 않는다 — 접근자 이름을 도메인 언어로 짓는다 (예: `id()`, `name()`).

### 응답 포맷 — CommonResponse<T>
- 모든 REST 성공 응답은 `com.reservation.common.presentation.CommonResponse<T>` 로 감싼다. 본문은 항상 `{ "data": <payload> }` 형태.
- HTTP status 는 `ResponseEntity.status(...)` 로만 지정:
  - 생성: `ResponseEntity.status(HttpStatus.CREATED).body(CommonResponse.of(...))`
  - 조회/수정: `ResponseEntity.ok(CommonResponse.of(...))`
  - 삭제 · 비우기: `ResponseEntity.ok(CommonResponse.of(null))` (204 NoContent 대신 200 + `data=null`)
- **`Location` 헤더 · URI 빌더를 사용하지 않는다**. 생성 결과도 body 의 `data` 로만 전달.
- 에러 응답은 `ErrorResponse` (common-infrastructure) 로 별도 포맷을 유지한다 — `CommonResponse` 로 감싸지 않는다. `@RestControllerAdvice` 가 직접 `ErrorResponse` 를 반환.
- `@WebMvcTest` 에서 성공 응답은 `$.data.*` 경로로, 에러 응답은 `$.code` / `$.status` 로 assert 한다.

### 컨트롤러 구조
```java
@RestController
@RequestMapping("/api/v1/<리소스>")
@RequiredArgsConstructor
public class XxxController {

    private final XxxApplicationService service;

    @PostMapping
    public ResponseEntity<CommonResponse<XxxResponse>> register(@RequestBody RegisterXxxRequest request) {
        XxxResult result = service.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.of(XxxResponse.of(result)));
    }
}
```

### Presentation 의존성 (선택 — 향후 별도 PR)
- `jakarta.validation` (`@NotBlank`, `@Min`, `@Valid`) 은 아직 도입하지 않는다. 입력 검증은 도메인 VO (`HotelName`, `StarRating` 등) 가 수행하고 `IllegalArgumentException` → 400 으로 매핑. `validation-starter` 도입은 별도 PR 승인 후.

### 참고 구현
- `hotel-service` PR-1.1b 전체 — Controller · Service · Repository · ExceptionHandler 의 실제 예시.
- `common-infrastructure/src/main/java/com/reservation/common/presentation/CommonResponse.java`

---

## 컨텍스트 최적화 (Claude Code 세션 운영)

본 저장소는 PR 단위 개발이 크고(1 PR ≈ 100k+ 토큰 소모) MSA 전체를 한 세션에서 진행하면 컨텍스트가 빠르게 소진된다. 다음 규칙을 지켜 품질 저하를 예방한다.

### 세션 분리
- **PR 머지 직후 `/clear` 로 세션 리셋**. 새 세션은 한 줄 복귀로 충분 — 예: "PR #11 merged. `docs/prd/...` 기반으로 PR-1.2 착수".
- CLAUDE.md · MEMORY.md · plan 파일은 매 세션 자동 로드되므로 도메인 지식 · 결정사항 · 진행 상황은 보존된다.
- 한 세션에 **최대 1~2 PR** 을 상한으로 한다. 그 이상이면 후반부 응답 품질이 떨어진다.

### 서브에이전트 결과 처리
- code-reviewer · test-reviewer · ddd-architect 등의 반환 전문(全文)을 대화 맥락에 남기지 않는다.
- **판정(APPROVED / NEEDS CHANGES / REJECT) + Critical · High 지적 요약** 만 보존하고 세부 근거는 리뷰 보고서(원한다면 `docs/reviews/` 에 파일로) 로 옮긴다.
- 다음 세션에서는 "code-reviewer: NEEDS CHANGES → Critical 2건 반영 후 APPROVE" 수준의 한 줄 요약만 있으면 충분.

### 디버깅 로그 정리
- 빌드 실패 · 테스트 실패 시의 `stdout/stderr` 와 HTML report grep 결과는 **원인 파악 직후 요약** 으로 압축한다.
- "Schema-validation: star_rating TINYINT vs INT" 같은 한 줄 원인만 남기고 스택트레이스는 버린다.
- 같은 파일을 여러 번 Read 하지 말 것. Edit 전 한 번만 필요한 범위로 읽는다.

### Plan · PRD 참조
- Plan 문서(`~/.claude/plans/...`)는 세션 시작 시 한 번만 로드하면 충분하며 중간에 전문을 재인용하지 않는다.
- PRD 도 관련 FR 번호만 언급하고 전문 복사 붙여넣기는 피한다.

### 권장 진행 사이클 (MSA 전체)
```
[새 세션 1] PR-X.Y 요구사항 재진술 + 구현 + 리뷰 반영 + 머지
    ↓ /clear
[새 세션 2] PR-X.(Y+1) 동일 사이클
    ↓ /clear
...
```

---

## 참고

### 내부
- 스킬: `.claude/skills/` — 각 단계별 지침 (`module-boundary` 포함)
- 에이전트: `.claude/agents/` — 독립 컨텍스트 리뷰어
- PRD: `docs/prd/`
- ADR: `docs/adr/`

### 외부
- Eric Evans, *Domain-Driven Design*
- Vaughn Vernon, *Implementing Domain-Driven Design*
- Chris Richardson, *Microservices Patterns*
- OWASP Top 10

---

**마지막 업데이트**: 2026-04-23

# CLAUDE.md

이 문서는 Claude Code가 본 프로젝트에서 작업할 때 반드시 따라야 할 기준과 워크플로우를 정의합니다.
모든 작업 시작 전 이 문서를 먼저 읽고, 단계별로 `.claude/skills/` 하위의 해당 SKILL.md를 참조하세요.

---

## 1. 프로젝트 개요

- **언어 / 런타임**: Java 21, Spring Boot 3.x
- **빌드 도구**: Gradle (Kotlin DSL)
- **아키텍처**: DDD (Domain-Driven Design) + Layered Architecture 스타일
- **DB**: 운영 - PostgreSQL / 테스트 - H2 또는 Testcontainers
- **ORM**: Spring Data JPA (단, JPA Entity는 Infrastructure 계층에만 위치)
- **테스트**: JUnit 5 (Jupiter), AssertJ, Mockito, Spring Boot Test
- **문서화**: JavaDoc + Spring REST Docs(또는 Swagger) + ADR

---

## 2. 핵심 원칙 (NEVER 위반 금지)

이 원칙은 모든 작업에 우선 적용됩니다. 예외는 없으며, 위반이 불가피한 경우 **반드시 사용자에게 근거를 먼저 질문**해야 합니다.

1. **도메인 객체 ≠ DB Entity**
   - `domain/model` 의 도메인 객체는 JPA/Hibernate 어노테이션(`@Entity`, `@Column`, `@Table` 등)을 **절대로** 포함하지 않는다.
   - DB 저장용 객체는 `infrastructure/persistence/entity` 하위의 `XxxJpaEntity` 로 분리한다.
   - 둘 사이의 변환은 `infrastructure/persistence/mapper` 의 Mapper 가 담당한다.

2. **의존성 방향은 항상 안쪽으로 향한다**
   - Domain ← Application ← Infrastructure / Presentation
   - Domain 계층은 Spring, JPA, Jackson 등 어떤 외부 프레임워크에도 의존하지 않는다.

3. **계층별 테스트는 서로 독립적이다**
   - Domain 테스트는 Spring 컨텍스트 없이 순수 JUnit 으로만 수행한다 (1초 이내 통과).
   - Application 테스트는 Repository/외부 API 를 Mock 으로 대체한다.
   - Infrastructure 테스트는 `@DataJpaTest` 등 슬라이스 테스트로 DB 만 검증한다.
   - Presentation 테스트는 `@WebMvcTest` 로 HTTP 경계만 검증하고, Application 은 Mock 으로 둔다.

4. **테스트 없는 코드는 머지 금지**
   - 모든 public 메서드와 분기는 최소 1개 이상의 테스트로 커버되어야 한다.
   - 테스트 코드는 프로덕션 코드와 동일한 PR 에 포함되어야 한다.

5. **Claude 는 코드 작성 전에 반드시 계획을 수립하고 사용자 승인을 받는다**
   - 모호한 요구사항을 임의로 해석하지 않는다.
   - 불명확한 부분은 추정하지 말고 질문한다.

---

## 3. 전체 워크플로우

사용자가 요구사항을 전달하면 아래 순서로 진행합니다. 각 단계별로 해당 SKILL.md 를 반드시 읽고 그 지침을 따릅니다.

```
[1] 요구사항 수신
        │
        ▼
[2] 코드 계획 수립 및 검증 ───▶  .claude/skills/code-planning/SKILL.md
        │  (도메인 모델링, API 설계, TodoList, 사용자 승인)
        ▼
[3] DDD 아키텍처 기반 구현 ───▶  .claude/skills/ddd-architecture/SKILL.md
        │  (도메인 → 애플리케이션 → 인프라 → 프레젠테이션 순)
        ▼
[4] 테스트 코드 작성 ─────────▶  .claude/skills/testing-junit/SKILL.md
        │  (계층별 Given-When-Then, Mock/Real 기준)
        ▼
[5] 문서화 ──────────────────▶  .claude/skills/documentation/SKILL.md
        │  (JavaDoc, README 업데이트, ADR)
        ▼
[6] 커밋 ────────────────────▶  .claude/skills/commit-convention/SKILL.md
        │  (Conventional Commits, 원자적 커밋)
        ▼
[7] PR 생성 ─────────────────▶  .claude/skills/pr-guidelines/SKILL.md
           (템플릿, 체크리스트, 리뷰 포인트)
```

**각 단계는 건너뛸 수 없습니다.** 예: 테스트 없이 커밋으로 진행하지 않으며, 계획 승인 없이 구현에 착수하지 않습니다.

---

## 4. 디렉토리 구조 (표준)

```
src/main/java/com/example/project/
├── domain/                          # 순수 도메인 (프레임워크 의존 금지)
│   ├── model/                       # Aggregate Root, Entity, Value Object
│   ├── repository/                  # Repository 인터페이스 (구현 아님)
│   ├── service/                     # Domain Service
│   ├── event/                       # Domain Event
│   └── exception/                   # Domain 예외
│
├── application/                     # Use Case / 트랜잭션 경계
│   ├── service/                     # Application Service
│   ├── dto/                         # Command / Query / Result
│   └── port/                        # 외부 시스템 호출 인터페이스 (out port)
│
├── infrastructure/                  # 기술 세부사항
│   ├── persistence/
│   │   ├── entity/                  # JPA Entity (XxxJpaEntity)
│   │   ├── repository/              # Spring Data JPA + Domain Repository 구현
│   │   └── mapper/                  # Domain ↔ JpaEntity 변환
│   ├── external/                    # 외부 API 어댑터
│   └── config/                      # Spring 설정
│
└── presentation/                    # HTTP / 메시징 진입점
    ├── controller/                  # REST Controller
    ├── dto/                         # Request / Response
    └── exception/                   # ExceptionHandler
```

테스트도 동일한 패키지 구조를 따릅니다 (`src/test/java/...`).

---

## 5. Claude Code 작업 시 주의사항

### 5.1 작업 시작 시
- `git status` 로 현재 브랜치와 변경사항을 먼저 확인한다.
- 새 기능이면 `feature/도메인-기능명` 브랜치를 생성 후 작업한다.
- 대규모 리팩토링이 필요한 경우 먼저 사용자에게 알리고 ADR 을 제안한다.

### 5.2 코드 수정 시
- **임의로 의존성(build.gradle) 을 추가하지 않는다.** 필요한 경우 사용자에게 제안한다.
- 기존 코드 스타일과 네이밍 컨벤션을 우선 따른다.
- 한 PR 은 **하나의 관심사**만 다룬다. 리팩토링과 기능 추가는 분리한다.
- `TODO`, `FIXME`, 주석 처리된 코드는 남기지 않는다. 필요하면 별도 이슈로 제안한다.

### 5.3 검증 명령어 (작업 완료 전 반드시 실행)
```bash
./gradlew clean build           # 전체 빌드 및 테스트
./gradlew test                  # 테스트만
./gradlew test --tests '*Domain*'   # 특정 계층 테스트
./gradlew check                 # 정적 분석 포함
```

모든 명령이 성공한 뒤에야 커밋/PR 단계로 진행한다.

### 5.4 실패 처리
- 테스트 실패 시: **절대로 `@Disabled`, `@Ignore`, `assumeTrue(false)` 등으로 회피하지 않는다.** 원인을 분석하고 수정한다.
- 빌드 실패 시: 경고를 에러로 취급한다 (`-Werror` 수준).
- 의도적으로 실패하는 테스트가 있다면 사용자에게 사유를 먼저 보고한다.

### 5.5 커뮤니케이션
- 추측이 필요한 부분은 추측하지 말고 **명확히 질문**한다.
- 작업 완료 시 다음을 보고한다:
  - 수정된 파일 목록
  - 추가된 테스트 개수와 커버리지
  - 남은 TODO(있다면)
  - 다음 제안 단계

---

## 6. 금지 사항 (Hard NO)

아래 사항은 어떠한 이유로도 수행하지 않습니다.

- ❌ 도메인 객체에 `@Entity`, `@Table`, `@Column`, `@GeneratedValue` 등 JPA 어노테이션 부착
- ❌ Controller 에서 Repository 직접 호출 (반드시 Application Service 경유)
- ❌ Application Service 에서 다른 Application Service 직접 호출 (Domain Service 또는 이벤트로 해결)
- ❌ 테스트를 건너뛰거나 비활성화
- ❌ `e.printStackTrace()`, `System.out.println` 등 프로덕션 코드의 디버그 출력
- ❌ 주석만으로 된 설명("무엇을" 이 아닌 "왜" 를 쓸 것)
- ❌ 매직 넘버 / 매직 스트링 (상수 또는 enum 사용)
- ❌ 하나의 커밋에 여러 관심사 혼합 (기능 + 리팩토링 + 포매팅)
- ❌ 사용자 승인 없는 공개 API 시그니처 변경

---

## 7. 참고 문서

- DDD: Eric Evans, *Domain-Driven Design*
- Hexagonal Architecture: Alistair Cockburn
- Clean Architecture: Robert C. Martin
- 프로젝트 내 ADR: `docs/adr/`
- 아키텍처 다이어그램: `docs/architecture/`

---

**마지막 업데이트**: 2026-04-20
**적용 범위**: 본 저장소 내 모든 Claude Code 작업

# Contributing to Hotel Reservation

이 저장소에 기여해 주셔서 감사합니다. 본 문서는 이슈 · 코드 · 문서 PR 을 보낼 때 따라야 할 **최소 가이드** 를 요약합니다.

프로젝트의 심층 규칙(DDD · MSA 경계 · 테스트 전략) 은 [`CLAUDE.md`](../CLAUDE.md) 와 [`.claude/skills/`](../.claude/skills/) 에 정의되어 있습니다. 본 문서는 그 중 기여자가 바로 알아야 하는 핵심만 발췌합니다.

---

## 1. 사전 준비

- Docker 24+, JDK 21, Git 2.30+ (로컬 구동 상세는 [`README.md`](../README.md) 참조)
- 저장소 fork → clone → `./gradlew clean build` 로 전체 빌드 통과 확인

## 2. 브랜치 전략

- **default branch = `dev`** — 모든 개발 통합 지점
- `main` 은 release / stable 전용. `main` 으로 직접 PR 금지
- 새 작업 루틴:
  ```bash
  git checkout dev
  git pull origin dev
  git checkout -b feature/<scope>-<action>
  ```
- 브랜치 네이밍 예시:
  - `feature/hotel-register-api`
  - `fix/reservation-double-decrement`
  - `refactor/domain-pricing-policy`
  - `chore/archunit-add-naming-rule`

## 3. 커밋 메시지 (Conventional Commits)

```
<type>(<scope>): <subject>

<body (왜 바뀌는가)>
```

- **type**: `feat` · `fix` · `refactor` · `perf` · `test` · `docs` · `chore` · `build` · `ci` · `style`
- **scope (선택)**: 영향 받는 서비스 · 모듈 (`hotel` · `contracts` · `common-infra` · `arch` 등)
- **원자적 커밋**: 한 커밋 = 하나의 논리적 변경. 리팩토링 + 기능 추가 혼합 금지
- 본문은 **"무엇을" 이 아닌 "왜"** 를 기술
- 상세: [`.claude/skills/commit-convention/SKILL.md`](../.claude/skills/commit-convention/SKILL.md)

## 4. 코드 스타일 · 아키텍처

- **언어 / 런타임**: Java 21 · Spring Boot 3.x · Gradle Kotlin DSL
- **아키텍처**: MSA + 서비스별 DDD Layered — 원칙 전문은 [`CLAUDE.md`](../CLAUDE.md)
- 핵심 금지:
  - 도메인 객체에 JPA / Spring 어노테이션 (`@Entity`, `@Component` 등)
  - Controller 에서 Repository 직접 호출 (Application Service 경유)
  - 다른 서비스 패키지 import — 통신은 `contracts` · `common-infrastructure` 만 허용
  - 매직 넘버 · 매직 스트링 (상수 / Enum 사용)
- 위 경계는 **ArchUnit 테스트가 자동으로 감지** 합니다 ([`.claude/skills/module-boundary/`](../.claude/skills/module-boundary/))

## 5. 테스트

- 모든 public 메서드와 분기를 테스트로 커버하는 것이 머지 조건
- **계층별 독립 테스트** 원칙:
  | 계층 | 방식 |
  |---|---|
  | Domain | Spring 없이 순수 JUnit |
  | Application | Repository · 외부 연동 Mock |
  | Infrastructure | `@DataJpaTest` 등 슬라이스 테스트 |
  | Presentation | `@WebMvcTest` |
- PR 생성 전 `./gradlew clean build` 통과 필수
- 상세: [`.claude/skills/testing-junit/SKILL.md`](../.claude/skills/testing-junit/SKILL.md)

## 6. Pull Request

- **타겟 브랜치: 반드시 `dev`**
- 제목은 Conventional Commits 그대로 (예: `feat(reservation): 예약 취소 API 추가`)
- 본문에 포함할 항목:
  1. **목적 (Why)** — 해결하려는 문제
  2. **변경 사항 (What)** — 계층/모듈별 요약
  3. **테스트** — 추가/수정된 테스트 · 실행 결과
  4. **체크리스트** — 빌드 · 원칙 준수 · 문서 갱신
  5. **리뷰 포인트** — 집중해 봐 주길 바라는 부분
  6. **Breaking Change** — 있다면 영향·마이그레이션 절차
- PR 크기 기준:
  | 크기 | 라인 수 | 권장 |
  |---|---|---|
  | S | < 100 | 이상적 |
  | M | 100–400 | OK |
  | L | 400–800 | 분할 고려 |
  | XL | > 800 | 반드시 분할 |
- **한 PR = 하나의 관심사**. 리팩토링과 기능 추가는 분리
- 리뷰에서 Critical / High 이슈가 해소된 뒤에만 merge
- 상세: [`.claude/skills/pr-guidelines/SKILL.md`](../.claude/skills/pr-guidelines/SKILL.md)

## 7. 이슈 보고

### 버그
- 재현 절차 (가능하면 최소 재현 코드)
- 기대 동작 · 실제 동작
- 환경 (OS · JDK · Docker 버전)
- 관련 로그 · 스택 트레이스

### 기능 제안
- 해결하려는 문제 (Why)
- 제안하는 해결 방법 (How) · 대안 검토
- 영향 받는 서비스 · 모듈

## 8. 문서 기여

- PRD 변경은 `docs/prd/` 파일 수정 + 필요 시 `docs/adr/` 에 결정 근거 추가
- ADR 이 한 번 **Accepted** 되면 원본을 뒤엎지 말고 **Superseded** 로 새 ADR 을 제안
- `README.md` 수정은 빠른 시작 · 포트 매핑 등 사용자 관점의 가독성을 우선

## 9. 질문 · 보안

- 설계 방향이 불명확한 시점에는 **Draft PR 또는 Discussion** 으로 일찍 논의해 주세요
- 보안 취약점은 공개 이슈 대신 메인테이너에게 **비공개 채널** 로 보고해 주세요

---

## 참고 문서

- [`CLAUDE.md`](../CLAUDE.md) — 프로젝트 최상위 규칙
- [`README.md`](../README.md) — 로컬 구동 · 아키텍처 개요 · 운영 배포 체크리스트
- [`docs/prd/`](../docs/prd/) — 제품 요구사항 문서
- [`docs/adr/`](../docs/adr/) — 아키텍처 결정 기록
- [`.claude/skills/`](../.claude/skills/) — 단계별 지침 (code-planning · ddd-architecture · testing-junit · commit-convention · pr-guidelines · module-boundary · documentation)

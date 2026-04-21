---
name: commit-convention
description: Git 커밋을 작성하거나 스테이징할 때 사용하는 스킬. Conventional Commits 형식 적용, 원자적 커밋(atomic commit), 커밋 타입(feat/fix/refactor/docs/test/chore/style/perf/build/ci) 선택 기준, 메시지 바디 작성법, 한국어/영어 혼용 규칙, 커밋 전 체크리스트를 포함한다. "커밋해줘", "commit", "git add", "커밋 메시지" 같은 표현이 나오거나 코드 구현/테스트/문서화가 완료된 직후 반드시 사용한다. 여러 관심사를 한 커밋에 섞지 않는 것이 핵심.
---

# Commit Convention - Git 커밋 규칙

이 스킬은 **코드, 테스트, 문서가 모두 완성된 후** 커밋을 만들 때 사용합니다.
좋은 커밋 히스토리는 **미래의 디버깅과 리뷰 속도** 를 결정합니다.

---

## 1. 3가지 핵심 원칙

1. **원자적(Atomic)**: 한 커밋은 **하나의 논리적 변경**만 담는다. 따로 롤백 가능해야 한다.
2. **자기 설명적**: 커밋 메시지만 보고 변경 내용을 이해할 수 있어야 한다.
3. **빌드 가능**: 각 커밋 시점에서 **컴파일되고 테스트가 통과**해야 한다 (bisect 를 위해).

---

## 2. 커밋 메시지 형식 (Conventional Commits)

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 2.1 필수/선택

- `type`: **필수**
- `scope`: 권장 (도메인/모듈)
- `subject`: **필수** (한 줄, 50자 이내, 마침표 없음)
- `body`: 권장 (변경 이유, "왜")
- `footer`: 선택 (Breaking Change, 이슈 링크)

### 2.2 예시

```
feat(order): 주문 취소 API 추가

배송 전 주문에 대해 취소를 허용하고 OrderCancelled 이벤트를 발행한다.
결제 환불은 이벤트 리스너에서 비동기로 처리한다.

- POST /api/v1/orders/{id}/cancel
- SHIPPED 이후 상태는 OrderCannotBeCancelledException 반환 (HTTP 400)
- CancellationReason VO 추가

Closes #142
```

---

## 3. Type 선택 기준

| Type | 용도 | 예시 |
|---|---|---|
| `feat` | 새로운 기능 (사용자/API 관점) | 새 엔드포인트, 새 도메인 기능 |
| `fix` | 버그 수정 | 잘못된 계산, NPE 수정 |
| `refactor` | 동작 변화 없는 내부 구조 개선 | 메서드 추출, 클래스 분리 |
| `perf` | 성능 개선 | 쿼리 최적화, 캐시 추가 |
| `test` | 테스트만 추가/수정 | 누락된 테스트 보강 |
| `docs` | 문서만 변경 | JavaDoc, README, ADR |
| `style` | 코드 포매팅 (동작 영향 없음) | import 정리, 공백 |
| `chore` | 빌드, 의존성, 기타 자잘한 작업 | 라이브러리 버전 업 |
| `build` | 빌드 시스템 자체 변경 | Gradle 설정 수정 |
| `ci` | CI/CD 파이프라인 변경 | GitHub Actions 수정 |
| `revert` | 이전 커밋 되돌리기 | |

### 3.1 헷갈리는 경우 판단법

**"이 변경이 사용자 또는 API 클라이언트에게 보이는가?"**
- 예 → `feat` 또는 `fix`
- 아니오 → `refactor`, `perf`, `test`, `docs`, `chore` 중 선택

**"테스트와 함께 동작이 바뀌는가?"**
- 동작 변화 O + 새 기능 → `feat`
- 동작 변화 O + 기존 버그 → `fix`
- 동작 변화 X → `refactor`

---

## 4. Scope 작성

scope 는 **도메인 / 모듈 / 계층** 중 가장 적합한 것을 쓴다.

```
feat(order): ...          # 주문 도메인
fix(payment): ...         # 결제 도메인
refactor(domain): ...     # 도메인 계층 전반
chore(deps): ...          # 의존성
docs(readme): ...         # README
test(order): ...          # 주문 테스트
```

- 여러 도메인에 걸치면 scope 생략 또는 가장 상위 도메인만 표기.
- 그러나 **여러 도메인에 걸친다는 것은 원자적 커밋이 아닐 수도 있다는 신호** 다. 분리 가능한지 검토한다.

---

## 5. Subject 작성 규칙

- **명령형(imperative)**: "추가한다" 가 아닌 "**추가**" — 영어로는 "Add", "Fix", "Update".
- **50자 이내**.
- **마침표 없음**.
- **대문자 시작 또는 한글** (팀 규칙에 맞춤).
- **WHAT 만**, WHY 는 body 에.

```
✅ feat(order): 주문 취소 API 추가
✅ fix(order): SHIPPED 상태 취소 시 400 반환
✅ refactor(payment): PaymentGateway 를 Domain 인터페이스로 분리

❌ feat(order): 오늘 주문 취소 기능을 새로 구현했습니다.   (길고, 과거형, 마침표)
❌ fix: 버그 수정                                         (무엇을 수정했는지 모름)
❌ update Order.java                                      (파일명 나열)
```

---

## 6. Body 작성 규칙

### 6.1 언제 쓰는가?
- 단순한 오타 수정, 포매팅 외에는 **항상** 쓰는 것을 권장.
- "왜" 이 변경이 필요했는지, "어떻게" 해결했는지 기술.

### 6.2 형식
- 한 줄 72자 이내로 줄바꿈.
- subject 와 **한 줄 띄운다**.
- 불릿(`-`) 사용 가능.

### 6.3 예시

```
feat(order): 주문 취소 시 재고 복원 이벤트 발행

기존에는 주문 취소 후 재고가 수동으로 복원되어 간헐적 누락이 발생했다.
OrderCancelled 이벤트에 lines 정보를 포함시켜 재고 모듈이 구독하도록
변경해 자동화한다.

- OrderCancelled 이벤트에 List<OrderLine> 필드 추가
- InventoryRestorationEventHandler (infrastructure) 추가
- 통합 테스트로 전체 플로우 검증

Refs #123
```

---

## 7. Footer 작성 규칙

### 7.1 Breaking Change

하위 호환성이 깨지는 경우 **반드시** 명시.

```
feat(order)!: 주문 조회 응답 스키마 변경

BREAKING CHANGE: GET /orders/{id} 응답에서 "orderItems" 필드가
"lines" 로 변경되었습니다. 클라이언트는 v2 엔드포인트로 마이그레이션
해야 합니다.
```

subject 에 `!` 를 붙이고 footer 에 `BREAKING CHANGE:` 로 시작하는 단락.

### 7.2 이슈 연결

- `Closes #142` — 머지되면 이슈 자동 종료.
- `Refs #142` — 단순 참조.
- `Related-to: #142`

---

## 8. 원자적 커밋 판단법

아래 질문에 하나라도 "아니오" 면 커밋을 **분리** 한다.

- [ ] 이 커밋을 되돌려도(revert) 다른 변경을 잃지 않는가?
- [ ] 이 커밋 시점에 빌드가 성공하는가?
- [ ] 이 커밋 시점에 테스트가 통과하는가?
- [ ] 커밋 메시지가 하나의 목적으로 요약되는가?
- [ ] 리팩토링과 기능 추가가 섞여있지 않은가?
- [ ] 자동 포매팅 변경이 실제 로직 변경과 섞여있지 않은가?

### 8.1 자주 발생하는 분리 필요 케이스

| 혼합된 변경 | 분리 방법 |
|---|---|
| 신규 기능 + 기존 코드 리팩토링 | 1) `refactor: ...` 2) `feat: ...` 순서로 커밋 |
| 기능 + 포매팅 | 1) `style: ...` 2) `feat: ...` |
| 기능 + 의존성 추가 | 1) `chore(deps): ...` 2) `feat: ...` |
| 여러 도메인 동시 수정 | 도메인별로 분리 |
| 프로덕션 + 테스트 | 보통 같은 커밋 (함께 빌드되어야 함) |

---

## 9. 언어 정책

### 9.1 기본 규칙
- **한국어 팀**: 한국어로 subject, body 작성 가능. type 은 항상 영어.
- **다국어/오픈소스 팀**: 전부 영어.

### 9.2 혼용 규칙
- 한 저장소 내에서 **한 언어로 통일**. 커밋마다 다르면 가독성 저하.
- 고유명사, 코드 식별자는 그대로: `Order`, `OrderCancelled`, `@Transactional`.

---

## 10. 커밋 전 체크리스트

`git commit` 을 실행하기 직전 아래를 확인한다.

- [ ] `./gradlew build` 가 성공하는가?
- [ ] `./gradlew test` 가 모두 통과하는가?
- [ ] `git diff --staged` 로 스테이징 내용을 **직접 눈으로** 확인했는가?
- [ ] 의도하지 않은 파일이 포함되지 않았는가? (`*.log`, `target/`, IDE 파일)
- [ ] 변경 전체가 하나의 목적인가?
- [ ] 커밋 메시지 subject 가 50자 이내, 명령형, 마침표 없음?
- [ ] body 에 "왜" 가 적혀 있는가? (단순 작업이 아니면)
- [ ] Breaking Change 라면 footer 에 명시했는가?
- [ ] 관련 이슈 번호가 있다면 `Closes #...` 를 넣었는가?

---

## 11. 예시 모음

### 11.1 기능 추가 (단일 도메인)

```
feat(order): 배송 전 주문 취소 지원

PLACED, PAID 상태의 주문을 취소할 수 있도록 Order.cancel() 을 추가한다.
SHIPPED 이후 상태는 OrderCannotBeCancelledException 을 던진다.

Closes #142
```

### 11.2 버그 수정

```
fix(order): OrderStatus.PAID 에서 취소가 거부되던 문제 수정

상태 전이 검증에서 PAID 가 누락되어 취소 API 가 400 을 반환하던 문제.
PLACED 와 PAID 모두 취소 가능하도록 조건 수정 + 회귀 테스트 추가.

Refs #158
```

### 11.3 리팩토링

```
refactor(domain): OrderPricingPolicy 를 Domain Service 로 추출

Order 내부에 있던 할인 계산 로직이 커지면서 책임이 섞여 OrderPricingPolicy
도메인 서비스로 분리했다. 동작 변화는 없으며 기존 테스트로 회귀를 검증.
```

### 11.4 문서

```
docs(adr): ADR-0005 추가 - 주문 이벤트 처리 비동기화

배송 전 주문 취소 응답 시간 개선을 위해 트랜잭션 커밋 후 이벤트 처리
구조를 도입한 결정 기록.
```

### 11.5 의존성

```
chore(deps): Spring Boot 3.2.4 → 3.3.0 업그레이드

Tomcat 보안 패치 적용. 릴리즈 노트 상 Breaking Change 없음을 확인했고
전체 테스트 통과.
```

### 11.6 Breaking Change

```
feat(api)!: GET /orders 응답을 페이지네이션 구조로 변경

BREAKING CHANGE: 응답이 List<OrderResponse> 에서
Page<OrderResponse>(content, totalElements, ...) 로 변경됨.
클라이언트는 v2 경로 또는 새 응답 스키마로 이전 필요.

Migration guide: docs/migration/2026-04-pagination.md
Closes #189
```

---

## 12. 자주 하는 실수

| 실수 | 교정 |
|---|---|
| `git commit -am "fix"` | 구체적 메시지 작성 후 body 에 사유 |
| 여러 기능을 한 커밋에 묶기 | `git add -p` 로 hunk 단위 스테이징 |
| 커밋 메시지에 파일명만 나열 | 의도를 적는다: "Why did this change?" |
| type 을 잘못 선택 (예: 새 기능을 `chore`) | 사용자에게 영향 있으면 `feat`/`fix` |
| 한국어/영어 혼용 | 팀 규칙에 맞게 통일 |
| Breaking Change 누락 | subject `!` 와 footer 명시 |

---

## 13. 유용한 Git 명령

```bash
# 상세 변경 확인 후 커밋
git diff --staged
git commit

# 방금 커밋 메시지 수정
git commit --amend

# hunk 단위로 선별 스테이징
git add -p <file>

# 과거 커밋 재구성 (push 전에만)
git rebase -i HEAD~3

# 직전 커밋에 작은 수정 합치기 (push 전)
git commit --fixup=<SHA>
git rebase -i --autosquash HEAD~5
```

⚠️ **이미 push 된 커밋은 amend/rebase 하지 않는다** (공동 작업 시). 문제가 있으면 새 커밋으로 수정.

---

## 14. 다음 단계

커밋이 완료되면:
- PR 생성 → `.claude/skills/pr-guidelines/SKILL.md`
- 여러 커밋을 하나의 PR 로 묶을 때의 기준과 템플릿 참조

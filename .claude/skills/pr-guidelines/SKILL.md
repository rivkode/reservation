---
name: pr-guidelines
description: Pull Request를 생성하거나 PR 설명을 작성할 때 사용하는 스킬. PR 제목 형식(Conventional Commits 기반), PR 본문 템플릿(목적/변경사항/테스트/체크리스트/리뷰 포인트/스크린샷), 브랜치 네이밍, PR 크기 기준, 셀프 리뷰, 머지 전략(squash/rebase/merge)을 포함한다. "PR 올려줘", "pull request", "PR 만들어줘", "리뷰 요청" 같은 언급이 있거나 커밋 완료 후 푸시 단계에서 반드시 사용한다. 작고 리뷰 가능한 PR 유지가 원칙.
---

# Pull Request Guidelines - PR 작성 및 리뷰 기준

이 스킬은 **커밋이 완료된 후** 원격 저장소에 PR 을 생성할 때 사용합니다.
PR 은 **리뷰를 위해 최적화** 되어야 하며, 작성자가 리뷰어를 위해 맥락을 충분히 제공해야 합니다.

---

## 1. 핵심 원칙

1. **작게 유지**: 400줄 이하, 파일 20개 이하가 이상적. 큰 PR 은 분할한다.
2. **단일 목적**: 한 PR 은 하나의 기능/버그/리팩토링만 담는다.
3. **맥락 제공**: 리뷰어가 변경 배경을 읽는 데 5분을 넘기지 않도록 한다.
4. **통과 가능한 상태로 열기**: CI 가 실패한 채로 리뷰 요청하지 않는다.
5. **셀프 리뷰 먼저**: PR 을 연 직후 본인이 먼저 diff 를 읽는다.

---

## 2. 브랜치 전략 & 네이밍

### 2.1 기본 브랜치
- **기본(default) 브랜치는 `dev`**. `main` 이 아니다.
- **모든 PR 의 base 브랜치는 `dev`**. 실수로 `main` 을 선택하지 않는다.
- 새 작업을 시작할 때 반드시 `git checkout dev && git pull origin dev` 후 분기한다.
- `main` 은 운영 릴리스용으로 별도 관리되며, `dev` → `main` 머지는 릴리스 담당자가 수행한다. 기능 PR 은 `main` 을 건드리지 않는다.

### 2.2 브랜치 네이밍

```
<type>/<scope>-<short-description>
```

예시:
- `feat/order-cancel-api`
- `fix/order-paid-cancel-rejection`
- `refactor/domain-pricing-policy`
- `chore/bump-spring-boot-3.3`

**규칙**:
- 소문자 + kebab-case
- 80자 이내
- 이슈 번호를 넣을 수도 있음: `feat/142-order-cancel-api`

---

## 3. PR 제목

**Conventional Commits 형식** 을 그대로 따른다.

```
<type>(<scope>): <subject>
```

예시:
- `feat(order): 주문 취소 API 추가`
- `fix(order): PAID 상태에서 취소 거부 문제 수정`
- `refactor(domain): OrderPricingPolicy 를 Domain Service 로 추출`

**이유**: PR squash merge 시 자동으로 커밋 메시지가 되며, CHANGELOG 자동 생성 도구와 호환된다.

---

## 4. PR 본문 템플릿

아래 템플릿을 `.github/pull_request_template.md` 로 커밋하고 모든 PR 에 사용한다.

```markdown
## 🎯 목적 (Why)

<!-- 이 PR 이 왜 필요한가? 해결하려는 문제 / 달성하려는 목표 -->

Closes #<이슈번호>

## 📋 변경 사항 (What)

<!-- 주요 변경 사항을 불릿으로. 파일 나열이 아닌 개념 수준으로. -->

- Domain: `Order.cancel(reason, now)` 추가, 상태 전이 검증
- Domain: `CancellationReason` Value Object 추가
- Application: `CancelOrderService`, `CancelOrderCommand` 추가
- Infrastructure: `orders` 테이블에 `cancelled_at`, `cancellation_reason` 컬럼 추가 (Flyway)
- Presentation: `POST /api/v1/orders/{id}/cancel` 엔드포인트 추가

## 🧪 테스트

<!-- 어떤 테스트를 추가/수정했는지, 수동 검증을 했다면 방법 -->

- [x] Domain 단위 테스트: `OrderTest` — 상태 전이 모든 분기
- [x] Application 단위 테스트: `CancelOrderServiceTest` — 성공/실패 케이스
- [x] Infrastructure 테스트: `OrderRepositoryImplTest` — `@DataJpaTest`
- [x] Presentation 테스트: `OrderControllerTest` — `@WebMvcTest`
- [x] 통합 테스트: `OrderCancellationIntegrationTest` — 전체 플로우

커버리지: Domain 98%, Application 94%, 전체 82%

## 🖼 스크린샷 / 실행 결과

<!-- UI 변경, API 응답 예시, 로그 등 -->

```http
POST /api/v1/orders/ORD-123/cancel
Content-Type: application/json

{ "reason": "CUSTOMER_REQUEST", "detail": "사이즈 변경" }

→ 200 OK
{ "orderId": "ORD-123", "status": "CANCELLED" }
```

## ⚠️ 주의사항 / 리뷰 포인트

<!-- 리뷰어가 특히 봐주었으면 하는 부분, 논의가 필요한 결정, 트레이드오프 -->

- `OrderCancelled` 이벤트는 `AFTER_COMMIT` 리스너에서 처리 → 환불/알림 실패가 취소 자체를 롤백시키지 않음 (ADR-0005 참조)
- `CancellationReason` 을 String 코드로만 관리 중. Enum 으로 바꿀지 후속 논의 필요.

## 🔄 Breaking Change

- [ ] **없음**
- [x] **있음**: 아래에 영향 범위와 마이그레이션 방법 기술
  <!-- 있는 경우에만 작성 -->

## ✅ 체크리스트

- [x] CLAUDE.md 의 원칙을 준수했는가?
- [x] 도메인 객체와 JpaEntity 가 분리되어 있는가?
- [x] 계층별 테스트가 독립적으로 통과하는가?
- [x] `./gradlew build` 가 로컬에서 성공하는가?
- [x] 새 public API 에 JavaDoc 을 작성했는가?
- [x] API 변경이 있다면 OpenAPI 스펙이 갱신되었는가?
- [x] 아키텍처 결정이 있다면 ADR 을 추가했는가?
- [x] CHANGELOG 의 Unreleased 섹션을 업데이트했는가?
- [x] 셀프 리뷰를 완료했는가?

## 📎 관련 문서

- ADR-0005: docs/adr/0005-async-order-event-processing.md
- 이슈: #142
- 설계 논의: <Slack/노션 링크>
```

---

## 5. 셀프 리뷰 체크리스트

PR 을 연 직후 **본인이 먼저** 아래를 확인한다. 기계적으로.

### 5.1 코드 품질
- [ ] 추가한 주석이 "왜" 를 설명하는가? ("무엇" 은 코드로)
- [ ] 변수/메서드 이름이 의도를 드러내는가?
- [ ] 반복되는 코드가 있는가? 추출할 만한가?
- [ ] 예외 처리가 누락된 경로가 없는가?
- [ ] 매직 넘버 / 매직 스트링이 남아있지 않은가?
- [ ] 죽은 코드, 주석 처리된 코드가 남아있지 않은가?

### 5.2 DDD 원칙
- [ ] 도메인에 JPA/Spring 어노테이션이 없는가?
- [ ] JpaEntity 와 Domain 객체가 분리되어 Mapper 를 통하는가?
- [ ] Controller 가 Application 만 호출하는가?
- [ ] Application Service 에 비즈니스 규칙이 새지 않았는가?
- [ ] `Clock` 등 비결정적 의존성이 주입 가능한가?

### 5.3 테스트
- [ ] 모든 public 메서드에 테스트가 있는가?
- [ ] 성공 케이스만이 아니라 실패/경계 케이스도 있는가?
- [ ] `@Disabled`, `@Ignore` 가 남아있지 않은가?
- [ ] Mock 이 과도하게 사용되지 않았는가?
- [ ] 계층별 테스트가 각자 독립적으로 통과하는가?

### 5.4 성능 / 보안
- [ ] N+1 쿼리를 만들지 않는가?
- [ ] 입력 검증이 Presentation 계층에 있는가?
- [ ] 권한 검증이 필요한 엔드포인트에 누락이 없는가?
- [ ] 민감 정보가 로그에 남지 않는가?

### 5.5 문서 / 커밋
- [ ] 관련 문서(README, ADR, OpenAPI)가 갱신되었는가?
- [ ] 커밋들이 원자적이고 각자 빌드 가능한가?
- [ ] PR 본문에 리뷰 포인트가 명시되어 있는가?

---

## 6. PR 크기 기준과 분할

### 6.1 권장 크기
- **S (이상적)**: 변경 < 100줄, 파일 < 10개 — 30분 내 리뷰
- **M**: 변경 100~400줄, 파일 10~20개 — 1시간 이내 리뷰
- **L (분할 고려)**: 변경 400~800줄
- **XL (반드시 분할)**: 변경 > 800줄

### 6.2 분할 전략

큰 PR 을 나누는 방법:

1. **계층별 분할** (선호): Domain → Application → Infrastructure → Presentation
   - 각 PR 이 독립적으로 빌드 가능
   - 리뷰어가 아래에서 위로 올라가며 이해 가능

2. **기능별 분할**: 취소 API 추가 → 환불 이벤트 리스너 → 알림 발송
   - 각각 단독으로 배포 가능해야 함 (feature flag 필요할 수도)

3. **리팩토링 선행**: 기능 추가 전 구조 정리를 별도 PR 로
   - `refactor: ...` → `feat: ...` 순서

### 6.3 스택 PR (Stacked PR)

연속된 변경은 스택으로 쌓는다:
```
dev ← pr1 (refactor) ← pr2 (feat part 1) ← pr3 (feat part 2)
```
- pr1 이 머지되면 pr2 의 base 를 `dev` 로 변경.
- 리뷰어가 맥락을 유지하기 쉬움.

---

## 7. Draft PR 활용

**아래 경우 Draft 로 먼저 연다**:
- 설계 방향을 미리 공유하고 싶을 때
- CI 결과를 보고 싶을 때
- 작업 중간에 피드백이 필요할 때

Draft 에서는 `[WIP]` 접두사 없이 GitHub 의 Draft 기능을 사용한다.

---

## 8. 머지 전략

### 8.1 기본 전략: **Squash and Merge**
- PR 의 여러 커밋이 `dev` 에 하나의 커밋으로 합쳐짐.
- `dev` 히스토리가 깔끔하게 유지됨.
- 커밋 메시지는 PR 제목 + 본문이 되므로 **PR 제목을 정확히 작성** 해야 함.

### 8.2 예외 케이스
- **Rebase and Merge**: 개별 커밋이 모두 의미 있고 독립적으로 빌드 가능할 때.
- **Merge Commit**: 대규모 기능 브랜치를 병합하면서 이력을 남기고 싶을 때 (드물게).

### 8.3 머지 전 최종 확인
- [ ] PR 의 base 브랜치가 `dev` 인가? (실수로 `main` 이 선택되지 않았는가?)
- [ ] 모든 리뷰어가 Approve 했는가?
- [ ] CI / 빌드가 녹색인가?
- [ ] 리뷰 중 논의된 변경 사항이 반영되었는가?
- [ ] PR 제목이 머지 후 히스토리에 남기 적절한가?
- [ ] `dev` 와 충돌이 없는가?

---

## 9. 리뷰 요청 커뮤니케이션

### 9.1 리뷰어 지정
- 코드의 해당 영역 오너(CODEOWNERS) 자동 지정.
- 특별한 리뷰가 필요하면 명시적으로 태그: "@user1 보안 관점에서 봐주세요."

### 9.2 리뷰 코멘트에 응답
- **모든 코멘트에 응답** (반영/반대/토론).
- 단순 수정은 "반영했습니다 (commit SHA)" 로 간단히.
- 반대 의견이면 근거를 명확히, 필요하면 회의로 이동.
- 리뷰어가 Re-request 를 쉽게 하도록 변경 후 `git push` 하고 코멘트 남긴다.

### 9.3 코멘트 용어 (리뷰어 관점)

약속된 prefix 를 사용하면 의도 전달이 빠름.

| Prefix | 의미 |
|---|---|
| `nit:` | 사소한 의견 (꼭 안 고쳐도 됨) |
| `question:` | 단순 질문 |
| `suggestion:` | 제안 (선택) |
| `issue:` | 반드시 수정 필요 |
| `blocking:` | 머지 차단 수준 이슈 |
| `praise:` | 칭찬 (문화적으로 중요) |

---

## 10. 자동화 (권장 설정)

### 10.1 GitHub Actions 체크
- 빌드 + 전체 테스트
- 정적 분석 (SpotBugs, PMD, Checkstyle)
- 테스트 커버리지 (JaCoCo + Codecov)
- 컨벤션 검증 (commitlint, PR title check)

### 10.2 Branch Protection Rules (`dev`, `main`)
`dev` (기본 브랜치) 와 `main` (릴리스 브랜치) 모두에 적용한다.
- [ ] Require a pull request before merging
- [ ] Require approvals (최소 1인)
- [ ] Dismiss stale approvals on new commits
- [ ] Require status checks (CI 통과)
- [ ] Require branches to be up to date
- [ ] Require conversation resolution

---

## 11. 안티 패턴

| 안티 패턴 | 올바른 방법 |
|---|---|
| "WIP: 일단 올려봅니다" 로 시작 | Draft PR 로 열고 Ready 전까지 맥락 정리 |
| PR 본문에 "코드 보면 알 수 있습니다" | 목적/변경/테스트를 항상 명시 |
| 1,500줄 넘는 단일 PR | 계층/기능별 분할 |
| 리뷰 중 전혀 다른 변경 추가 | 새 PR 로 분리 |
| CI 실패한 채로 리뷰 요청 | 녹색 만든 후 요청 |
| 리뷰 코멘트에 응답하지 않음 | 모든 코멘트에 답 |
| 리뷰어가 지적한 것만 고치고 유사 케이스 방치 | "같은 패턴을 X 도 수정했습니다" |

---

## 12. 실전 예시

### 12.1 좋은 PR 예시 (요약)

```
제목: feat(order): 배송 전 주문 취소 API 추가

목적: 고객의 주문 취소 자가 해결 비율을 높이기 위한 기능 (이슈 #142)

변경: Domain/Application/Infra/Presentation 4계층에 걸쳐 Order.cancel
      플로우 추가. OrderCancelled 이벤트를 AFTER_COMMIT 에서 처리.

테스트: 총 27개 테스트 추가 (Domain 15 / Application 6 / Infra 3 / Web 3)
        커버리지 Domain 98%, 전체 82%

리뷰 포인트:
1. 이벤트 비동기 처리는 ADR-0005 에서 결정됨
2. CancellationReason 을 String 으로 둔 이유는 PR 설명 참조

체크리스트: 모두 체크
```

### 12.2 나쁜 PR 예시
```
제목: update
본문: (비어있음)
변경: 1,200줄, 여러 도메인 혼재, 포매팅과 기능이 섞임
```

---

## 13. 최종 요약

1. **작은 PR** 을 **자주** 올린다.
2. PR 은 **문서** 다. 미래의 디버깅과 온보딩을 위해 맥락을 남긴다.
3. **셀프 리뷰** 가 최고의 리뷰다.
4. CLAUDE.md 의 모든 원칙이 PR 체크리스트에 반영되었는지 확인.
5. 머지 후에도 부작용이 없는지 모니터링한다.

---

**이로써 전체 워크플로우가 완료됩니다**:
```
요구사항 → 계획 → DDD 구현 → 테스트 → 문서 → 커밋 → PR → 리뷰 → 머지
```

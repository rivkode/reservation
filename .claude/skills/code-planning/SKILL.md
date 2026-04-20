---
name: code-planning
description: 사용자가 Java/Spring 프로젝트에서 새로운 기능 구현, 버그 수정, 리팩토링 등 어떤 코드 작업을 요청할 때 가장 먼저 사용하는 스킬. 요구사항 분석, 도메인 모델링 초안, 영향 범위 파악, API 설계, TodoList 작성, 사용자 승인 절차를 포함한다. "구현해줘", "추가해줘", "만들어줘", "고쳐줘", "리팩토링" 등 코드를 건드리는 모든 요청에서 먼저 이 스킬을 거친 뒤 다른 스킬로 이동한다. 계획 없이 바로 코드를 작성하지 않도록 반드시 사용.
---

# Code Planning - 요구사항 분석 및 계획 검증

이 스킬은 **모든 코드 작업의 진입점**입니다. 요구사항을 받으면 코드를 한 줄이라도 작성하기 전에 반드시 이 단계를 완료해야 합니다.

---

## 1. 이 단계의 목표

- 요구사항의 **모호함 제거**
- 도메인 관점에서 **올바른 추상화 수준** 찾기
- **영향 범위**(변경 파일, 마이그레이션, API 호환성) 파악
- **작업 순서** (TodoList) 확정
- 사용자의 **명시적 승인** 받기

---

## 2. 실행 순서

### Step 1. 요구사항 재진술 (Restate)

사용자의 요청을 **내 말로 다시 정리**해서 보여준다. 이때 해석이 필요한 부분은 명시한다.

```
[요구사항 재진술]
사용자 요청: "주문 취소 기능을 추가해줘"

내 이해:
- 주문(Order) 에 대한 취소(cancel) 오퍼레이션 추가
- 이미 배송된(SHIPPED) 주문은 취소 불가로 가정
- 결제 환불은 별도 도메인 이벤트로 처리 (OrderCancelled 이벤트 발행)
- 취소 사유(reason) 를 필수로 받는다고 가정

확인이 필요한 점:
1. PARTIAL_CANCEL (일부 품목만 취소) 을 지원해야 하는가?
2. 취소 가능 시간 제한이 있는가? (예: 주문 후 24시간)
3. 환불 처리 시스템은 기존 것이 있는가, 새로 만들어야 하는가?
```

**불명확한 점이 2개 이상이면 여기서 멈추고 사용자에게 질문한다.** 추측해서 진행하지 않는다.

### Step 2. 도메인 관점 분석

아래 질문에 답을 적어본다:

| 질문 | 답변 예시 |
|---|---|
| 어떤 Aggregate 가 관련되는가? | `Order` Aggregate |
| 새로운 도메인 개념이 필요한가? | `CancellationReason` (Value Object) |
| 상태 전이가 있는가? | `PLACED → CANCELLED`, `PAID → CANCELLED` |
| 불변식(invariant) 은 무엇인가? | SHIPPED 이후에는 cancel() 호출 불가 |
| 도메인 이벤트가 필요한가? | `OrderCancelled(orderId, reason, cancelledAt)` |
| 기존 Aggregate 수정인가, 새 Aggregate 인가? | 기존 `Order` 수정 |

### Step 3. 계층별 영향 파악

변경이 필요한 계층과 파일을 미리 열거한다.

```
[영향 범위]
Domain:
  - Order.java              (cancel 메서드 추가, 상태 전이 검증)
  - OrderStatus.java        (CANCELLED 추가 - 이미 있으면 skip)
  - CancellationReason.java (신규 Value Object)
  - OrderCancelled.java     (신규 Domain Event)

Application:
  - CancelOrderService.java (신규)
  - CancelOrderCommand.java (신규)

Infrastructure:
  - OrderJpaEntity.java     (status 필드 매핑 확인, cancelReason 컬럼 추가)
  - OrderMapper.java        (신규 필드 매핑)
  - V2026_04_20__add_cancel_reason.sql (Flyway 마이그레이션)

Presentation:
  - OrderController.java    (POST /orders/{id}/cancel 추가)
  - CancelOrderRequest.java (신규 DTO)
```

### Step 4. API 설계 (해당 시)

REST API 가 추가/변경되는 경우:

```
POST /api/v1/orders/{orderId}/cancel
Content-Type: application/json

Request Body:
{
  "reason": "CUSTOMER_REQUEST",
  "detail": "사이즈 변경"
}

Response: 200 OK
{
  "orderId": "...",
  "status": "CANCELLED",
  "cancelledAt": "2026-04-20T10:00:00Z"
}

Error:
  400 - 이미 배송된 주문
  404 - 주문 없음
  409 - 이미 취소된 주문
```

### Step 5. TodoList 작성

구현 순서를 명확한 체크리스트로 만든다. **도메인부터 바깥쪽으로** 진행한다.

```
[ ] 1. Domain: OrderStatus.CANCELLED 추가 및 상태 전이 검증
[ ] 2. Domain: CancellationReason VO 구현 + 단위 테스트
[ ] 3. Domain: Order.cancel(reason) 메서드 구현 + 단위 테스트
[ ] 4. Domain: OrderCancelled 이벤트 정의 + Order 에서 발행
[ ] 5. Application: CancelOrderCommand, CancelOrderService 구현
[ ] 6. Application: CancelOrderService 단위 테스트 (Mock Repository)
[ ] 7. Infrastructure: OrderJpaEntity 필드 추가, OrderMapper 수정
[ ] 8. Infrastructure: Flyway 마이그레이션 스크립트 작성
[ ] 9. Infrastructure: @DataJpaTest 로 Repository 검증
[ ] 10. Presentation: Controller + Request DTO 구현
[ ] 11. Presentation: @WebMvcTest 로 Controller 검증
[ ] 12. 통합 테스트: 전체 플로우 @SpringBootTest
[ ] 13. 문서 갱신: OpenAPI 스펙, README, ADR (필요 시)
[ ] 14. 커밋 및 PR
```

### Step 6. 사용자 승인 요청

Step 1~5 의 결과를 **한 번에** 사용자에게 보여주고 명시적 확인을 받는다.

```
위 계획대로 진행해도 될까요?
수정이 필요한 부분이나 빠진 요구사항이 있다면 알려주세요.
```

**승인 없이 Step 7(구현)로 넘어가지 않는다.**

---

## 3. 질문 템플릿 (자주 쓰는 유형)

요구사항이 모호할 때 사용할 질문 패턴:

- **경계 조건**: "X 가 0 이거나 null 인 경우 어떻게 처리하나요?"
- **동시성**: "같은 리소스에 대한 동시 요청이 들어오면 어떻게 되어야 하나요?"
- **트랜잭션 경계**: "A 실패 시 B 는 롤백되어야 하나요, 아니면 독립적인가요?"
- **하위 호환**: "기존 API 클라이언트가 있나요? 있다면 Breaking Change 허용 범위는요?"
- **성능 요구**: "예상 트래픽이나 데이터 크기 기준이 있나요?"
- **권한**: "이 기능을 호출할 수 있는 주체는 누구인가요?"

---

## 4. 체크리스트 (Step 6 전에 자가 검증)

- [ ] 요구사항을 **내 말로** 정리했는가?
- [ ] 불명확한 점을 **모두** 질문 목록에 넣었거나, 명시적 가정으로 적었는가?
- [ ] 관련된 Aggregate / 도메인 개념을 식별했는가?
- [ ] 상태 전이, 불변식, 이벤트를 분석했는가?
- [ ] 영향 받는 파일을 계층별로 나열했는가?
- [ ] API 변경이 있다면 시그니처, 에러 케이스까지 포함했는가?
- [ ] TodoList 가 **도메인 → 애플리케이션 → 인프라 → 프레젠테이션** 순서인가?
- [ ] DB 스키마 변경이 있다면 마이그레이션 항목을 넣었는가?
- [ ] 테스트 항목이 계층별로 분리되어 있는가?

---

## 5. 안티 패턴 (하지 말 것)

1. **"일단 만들고 나서 물어보자"** — 돌이키기 어렵다.
2. **Controller 부터 작성** — 도메인이 흔들리면 전부 다시 짜야 한다.
3. **"아마 이럴 것이다" 라고 추측** — 요구사항을 임의로 확장하는 가장 흔한 실수.
4. **TodoList 없이 진행** — 중간에 범위가 팽창한다 (scope creep).
5. **DB 스키마부터 설계** — 도메인이 DB 에 종속되는 전형적 안티 패턴.

---

## 6. 다음 단계

계획이 승인되면:
- 구현 단계로 이동 → `.claude/skills/ddd-architecture/SKILL.md` 참조
- TodoList 의 각 항목을 순서대로 진행
- 한 항목이 끝날 때마다 체크 표시 후 다음으로

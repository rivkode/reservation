---
name: testing-junit
description: Java/Spring 프로젝트에서 JUnit 5 기반 테스트 코드를 작성할 때 사용한다. 계층별 테스트 전략(Domain/Application/Infrastructure/Presentation), Mock 사용 기준 vs 실제 객체 사용 기준, Given-When-Then 구조, Spring 슬라이스 테스트 선택 기준을 포함한다. "테스트 작성", "단위 테스트", "통합 테스트", "Mockito", "JUnit" 언급이 있거나 DDD 구현 후 검증 단계에서 항상 이 스킬을 먼저 확인한다. 계층 간 의존성 없는 독립 테스트 원칙을 반드시 준수.
---

# Testing with JUnit 5

DDD 구조에서 **계층별로 독립적이고 빠른** 테스트를 작성하기 위한 지침.

---

## 1. 핵심 원칙

1. **계층별 독립 테스트** — 각 계층은 자신의 책임만 검증. 다른 계층이 망가져도 내 테스트는 동작.
2. **Mock은 경계에만** — 순수 객체는 그대로 쓴다.
3. **테스트도 문서** — 이름만 봐도 사양이 보여야 함.
4. **빠른 피드백** — Domain 테스트 전체가 1초 이내.
5. **결정론적** — 시간/순서에 의존하지 않음.

---

## 2. 계층별 전략 (요약)

| 계층 | 어노테이션 | Spring | DB | Mock 대상 |
|---|---|---|---|---|
| Domain | 없음 (순수 JUnit) | ❌ | ❌ | 없음 |
| Application | `@ExtendWith(MockitoExtension.class)` | ❌ | ❌ | Repository, 외부 시스템 연동 인터페이스, EventPublisher |
| Infrastructure (JPA) | `@DataJpaTest` | 슬라이스 | ✅ | 없음 |
| Infrastructure (외부 API) | `@RestClientTest` / WireMock | 슬라이스 | ❌ | HTTP 서버 |
| Presentation | `@WebMvcTest` | 슬라이스 | ❌ | Application Service |
| End-to-End | `@SpringBootTest` | 전체 | ✅ | 최소화 |

**반드시**: 분류와 어노테이션이 일치해야 한다. Domain 테스트에 `@SpringBootTest` 가 보이면 즉시 수정.

---

## 3. Mock vs 실제 객체 사용 기준 ⭐

### 3.1 Mock을 **써야** 하는 것
- 외부 시스템 경계: **DB, 외부 HTTP API, 메시지 큐, 파일 시스템, 이메일**
- 비결정적 요소: **Clock, Random, UUID**
- 느리거나 비싼 작업
- **Repository 인터페이스, 외부 연동용 Domain 인터페이스**

### 3.2 Mock을 **쓰면 안** 되는 것
- **Value Object** (`Money`, `Email`, `OrderId`) → 실제 객체
- **Entity / Aggregate Root** (`Order`, `Customer`) → 실제 객체
- 순수 Domain Service (외부 의존 없음) → 실제 객체
- DTO, Command, Query → 실제 객체
- 테스트 대상 자체(SUT) → 당연히 실제

### 3.3 5가지 판단 질문

```
Q1. 외부 리소스(DB, 네트워크, 시간)에 접근?   → Yes: Mock
Q2. Repository/외부 연동 인터페이스?          → Yes: Mock
Q3. 실제로 쓰면 테스트가 1초 이상 걸리는가?    → Yes: Mock
Q4. 순수 계산 로직만 있는가?                   → Yes: 실제 객체
Q5. Value Object 또는 DTO 인가?                → Yes: 실제 객체
```

### 3.4 흔한 오용

```java
// ❌ VO를 Mock
Money money = mock(Money.class);
when(money.amount()).thenReturn(BigDecimal.TEN);

// ✅
Money money = Money.of(10, "KRW");
```

```java
// ❌ Aggregate를 Mock — "호출되었는가"만 확인, 실제 로직 미검증
Order order = mock(Order.class);
service.cancel(...);
verify(order).cancel(any(), any());

// ✅ 실제 객체로 상태 변화 검증
Order order = Order.place(...);
given(repository.findById(orderId)).willReturn(Optional.of(order));
service.cancel(command);
assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
```

---

## 4. Given-When-Then 구조

모든 테스트는 3 블록으로 명확히 분리. 빈 줄로 구분.

```java
@Test
@DisplayName("배송되지 않은 주문은 정상적으로 취소된다")
void cancel_whenOrderNotShipped_shouldMarkAsCancelled() {
    // given
    Order order = Order.place(...);
    CancellationReason reason = new CancellationReason("CUSTOMER_REQUEST");
    Instant cancelTime = Instant.parse("2026-04-20T11:00:00Z");

    // when
    order.cancel(reason, cancelTime);

    // then
    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.cancelledAt()).isEqualTo(cancelTime);
}
```

---

## 5. 테스트 이름 규칙

**형식**: `<메서드명>_<조건>_<기대결과>`

```java
@Test
@DisplayName("이미 배송된 주문은 취소할 수 없고 예외가 발생한다")
void cancel_whenOrderShipped_shouldThrow() { ... }
```

---

## 6. 계층별 상세 규칙

### 6.1 Domain 테스트
- **Spring 컨텍스트 금지** (`@SpringBootTest`, `@ExtendWith(SpringExtension.class)` 금지)
- Mockito 거의 사용 안 함
- `@Nested` 로 케이스 그룹화
- 상태 전이의 **모든 분기** 테스트

### 6.2 Application Service 테스트
- `@ExtendWith(MockitoExtension.class)` 사용
- **Repository, 외부 연동 인터페이스, EventPublisher만 Mock**
- Domain 객체는 실제로 생성
- `Clock.fixed(...)` 로 시간 고정
- 성공 + **모든 예외 경로** 테스트
- Mock 검증은 "호출 여부" 뿐 아니라 **인자, 횟수**까지

### 6.3 Infrastructure (JPA) 테스트
- `@DataJpaTest` 사용
- `TestEntityManager.flush() + clear()` 후 조회 (1차 캐시 우회)
- 실제 DB 제약(unique, not null) 검증

### 6.4 Presentation 테스트
- `@WebMvcTest(XxxController.class)` 로 **특정 Controller만**
- Application Service는 `@MockitoBean`
- HTTP 상태, JSON 구조, 입력 검증 실패(400), 예외 매핑(404, 409) 모두 검증

### 6.5 통합 테스트
- `@SpringBootTest` 는 **10개 이하**로 제한
- 크리티컬 플로우(결제, 주문)만
- Testcontainers/WireMock 으로 외부 시스템 대체

계층별 전체 예시 코드 → `references/examples-by-layer.md`

---

## 7. 커버리지 기준

- Domain: 분기 커버리지 **95% 이상**
- Application: 라인 커버리지 **90% 이상**
- Infrastructure: 정상/예외 각 1개 이상
- Presentation: 성공, 검증 실패, 예외 매핑 각 1개 이상
- 전체: 라인 커버리지 **80% 이상** (JaCoCo)

커버리지 숫자보다 **의미 있는 분기**를 놓치지 않는 것이 중요.

---

## 8. Fixture 관리

도메인 객체 생성이 복잡하면 **Test Object Mother** 패턴으로 빌더 분리:
`src/test/java/.../fixture/OrderFixtures.java` → `anOrder().withStatus(...).build()`

상세 예시 → `references/fixtures.md`

---

## 9. 주요 안티패턴 (12종)

Value Object Mocking, Aggregate Mocking, 모든 것을 @SpringBootTest, Happy Path Only, `LocalDateTime.now()` 직접 호출, `Thread.sleep()`, 무의미한 테스트 이름, 과도한 verify, `@Disabled` 방치, 한 테스트에 여러 시나리오, JPA 테스트에 flush/clear 누락, Controller 테스트에 `@SpringBootTest`.

상세 사례와 해결책 → `references/anti-patterns.md`

---

## 10. 실행 명령

```bash
./gradlew test                              # 전체
./gradlew test --tests '*domain*'           # 계층별
./gradlew test jacocoTestReport             # 커버리지
```

모든 테스트 통과 + 커버리지 기준 만족 후 다음 단계로.

---

## 다음 단계

→ `.claude/skills/documentation/SKILL.md`

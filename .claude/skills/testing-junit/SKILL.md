---
name: testing-junit
description: Java/Spring 프로젝트에서 JUnit 5 기반 테스트 코드를 작성할 때 반드시 사용하는 스킬. 계층별 테스트 전략(Domain/Application/Infrastructure/Presentation), Mock 사용 기준 vs 실제 객체 사용 기준, Given-When-Then 구조, 테스트 이름 규칙, Spring 슬라이스 테스트(@DataJpaTest, @WebMvcTest, @SpringBootTest) 선택 기준, Fixture 관리 방식을 포함한다. "테스트 작성", "단위 테스트", "통합 테스트", "Mockito", "JUnit" 같은 언급이 있거나 DDD 구현 후 검증 단계에서 항상 이 스킬을 먼저 확인한다. 계층 간 의존성 없는 독립 테스트 원칙을 반드시 준수.
---

# Testing with JUnit 5 - 계층별 테스트 전략

이 스킬은 DDD 구조에서 **계층별로 독립적이고 빠른** 테스트 를 작성하기 위한 지침입니다.

---

## 1. 핵심 원칙

1. **계층별 독립 테스트**: 각 계층은 자신의 책임만 검증한다. 다른 계층이 망가져도 내 테스트는 동작해야 한다.
2. **Mock 은 최소한으로**: Mock 은 **경계를 넘나드는 의존성**에만 사용한다. 순수 객체는 그대로 쓴다.
3. **테스트도 문서**: 테스트 이름만 봐도 사양을 이해할 수 있어야 한다.
4. **빠른 피드백**: Domain 테스트는 전체가 1초 이내에 끝나야 한다.
5. **결정론적**: 실행 시간/환경/순서에 따라 결과가 달라지면 안 된다.

---

## 2. 계층별 테스트 전략 (요약표)

| 계층 | 사용 어노테이션 | Spring 컨텍스트 | DB | Mock 사용 | 목적 |
|---|---|---|---|---|---|
| Domain | 없음 (순수 JUnit) | ❌ | ❌ | ❌ (기본) | 비즈니스 규칙, 불변식, 상태 전이 |
| Application | `@ExtendWith(MockitoExtension.class)` | ❌ | ❌ | ✅ Repository, Port | Use case 오케스트레이션 |
| Infrastructure (JPA) | `@DataJpaTest` | 슬라이스 | ✅ (H2/Testcontainers) | ❌ | 실제 쿼리, Mapper 변환 |
| Infrastructure (외부 API) | `@RestClientTest` 등 | 슬라이스 | ❌ | ✅ (MockServer) | 직렬화, 에러 매핑 |
| Presentation | `@WebMvcTest` | 슬라이스 | ❌ | ✅ Application Service | HTTP 입출력, 검증, 직렬화 |
| End-to-End | `@SpringBootTest` | 전체 | ✅ | 최소화 | 크리티컬 플로우만 |

---

## 3. Mock vs 실제 객체 사용 기준 (반드시 숙지)

아래 기준은 **예외 없이** 적용합니다.

### 3.1 Mock 을 써야 하는 것
- **외부 시스템 경계**: DB, 외부 HTTP API, 메시지 큐, 파일 시스템, 이메일 전송
- **비결정적 요소**: 현재 시각(`Clock`), 랜덤(`Random`), UUID 생성기
- **느리거나 비싼 작업**: 네트워크 호출, 큰 파일 I/O
- **테스트 대상의 협력자 중 복잡한 의존성 체인을 가진 것**

### 3.2 Mock 을 쓰면 안 되는 것
- **Value Object**: `Money`, `Email`, `OrderId` → 그대로 사용
- **Entity / Aggregate Root**: `Order`, `Customer` → 실제 객체 사용
- **순수 Domain Service**: 외부 의존성이 없으면 실제 객체 사용
- **DTO, Command, Query**: 그대로 사용
- **테스트 대상 자체** (SUT): 당연히 실제 객체

### 3.3 판단 체크리스트
의존성 하나를 보고 아래 질문에 답한다:

```
Q1. 이 객체가 외부 리소스(DB, 네트워크, 시간)에 접근하는가?  → Yes: Mock
Q2. 이 객체가 인터페이스로 정의된 Port/Repository 인가?      → Yes: Mock
Q3. 이 객체를 실제로 사용하면 테스트가 1초 이상 걸리는가?     → Yes: Mock
Q4. 이 객체가 순수 계산 로직만 있는가?                        → Yes: 실제 객체
Q5. 이 객체가 Value Object 또는 DTO 인가?                     → Yes: 실제 객체
```

### 3.4 빈번한 오용 예시

```java
// ❌ 잘못된 예: Money(VO)를 Mock
Money money = mock(Money.class);
when(money.amount()).thenReturn(BigDecimal.TEN);

// ✅ 올바른 예
Money money = Money.of(10, "KRW");
```

```java
// ❌ 잘못된 예: Order 를 Mock해서 cancel 검증
Order order = mock(Order.class);
verify(order).cancel(any(), any());   // 테스트가 "메서드 호출 여부"만 확인, 실제 로직 미검증

// ✅ 올바른 예
Order order = Order.place(...);
cancelOrderService.cancel(...);
assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
```

---

## 4. Given-When-Then 구조 (필수)

모든 테스트는 3 블록으로 명확히 나눈다. 빈 줄로 구분한다.

```java
@Test
@DisplayName("배송되지 않은 주문은 정상적으로 취소된다")
void cancel_whenOrderNotShipped_shouldMarkAsCancelled() {
    // given
    Order order = Order.place(
        OrderId.generate(),
        new CustomerId("CUST-1"),
        List.of(OrderLine.of("PROD-1", 1, Money.of(10_000, "KRW"))),
        Instant.parse("2026-04-20T10:00:00Z")
    );
    CancellationReason reason = new CancellationReason("CUSTOMER_REQUEST");
    Instant cancelTime = Instant.parse("2026-04-20T11:00:00Z");

    // when
    order.cancel(reason, cancelTime);

    // then
    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.cancelledAt()).isEqualTo(cancelTime);
    assertThat(order.cancellationReason()).isEqualTo(reason);
}
```

---

## 5. 테스트 이름 규칙

**형식**: `<메서드명>_<조건>_<기대결과>`

- `cancel_whenOrderIsShipped_shouldThrowException`
- `place_whenOrderLinesEmpty_shouldThrowIllegalArgument`
- `findById_whenOrderNotExists_shouldReturnEmpty`

**`@DisplayName` 한글 병기 권장**:
```java
@Test
@DisplayName("이미 배송된 주문은 취소할 수 없고 예외가 발생한다")
void cancel_whenOrderShipped_shouldThrow() { ... }
```

---

## 6. 계층별 상세 가이드

### 6.1 Domain 테스트

**어노테이션**: 없음. 순수 JUnit + AssertJ.
**실행 시간**: 테스트당 10ms 이하 목표.

```java
// src/test/java/.../domain/model/order/OrderTest.java
class OrderTest {

    private final Instant FIXED_NOW = Instant.parse("2026-04-20T10:00:00Z");

    @Nested
    @DisplayName("주문 생성")
    class Place {
        @Test
        @DisplayName("주문 품목이 없으면 생성에 실패한다")
        void place_whenLinesEmpty_shouldThrow() {
            assertThatThrownBy(() -> Order.place(
                OrderId.generate(), new CustomerId("C1"), List.of(), FIXED_NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최소 1개");
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class Cancel {
        @Test
        @DisplayName("배송된 주문은 취소할 수 없다")
        void cancel_whenShipped_shouldThrow() {
            Order order = anOrder().withStatus(OrderStatus.SHIPPED).build();
            assertThatThrownBy(() ->
                order.cancel(new CancellationReason("REASON"), FIXED_NOW))
                .isInstanceOf(OrderCannotBeCancelledException.class);
        }
    }
}
```

**체크리스트**:
- [ ] Spring 컨텍스트를 띄우지 않는가? (`@SpringBootTest` 금지)
- [ ] Mockito 를 쓰지 않는가?
- [ ] `@Nested` 로 케이스 그룹을 나눴는가?
- [ ] 상태 전이의 모든 분기를 테스트했는가?

### 6.2 Application Service 테스트

**어노테이션**: `@ExtendWith(MockitoExtension.class)`
**Repository, Port 는 Mock**, Domain 객체는 실제로 생성.

```java
@ExtendWith(MockitoExtension.class)
class CancelOrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock DomainEventPublisher eventPublisher;
    Clock fixedClock = Clock.fixed(Instant.parse("2026-04-20T11:00:00Z"), ZoneOffset.UTC);

    CancelOrderService sut;

    @BeforeEach
    void setUp() {
        sut = new CancelOrderService(orderRepository, eventPublisher, fixedClock);
    }

    @Test
    @DisplayName("주문을 취소하면 상태가 변경되고 이벤트가 발행된다")
    void cancel_shouldCancelAndPublishEvent() {
        // given
        OrderId orderId = OrderId.generate();
        Order order = Order.place(orderId, new CustomerId("C1"),
            List.of(OrderLine.of("P1", 1, Money.of(10_000, "KRW"))),
            Instant.parse("2026-04-20T10:00:00Z"));
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        CancelOrderCommand command = new CancelOrderCommand(
            orderId, new CancellationReason("CUSTOMER_REQUEST"));

        // when
        sut.cancel(command);

        // then
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        then(orderRepository).should().save(order);
        then(eventPublisher).should().publish(any(OrderCancelled.class));
    }

    @Test
    @DisplayName("존재하지 않는 주문 취소 시 예외가 발생한다")
    void cancel_whenOrderNotFound_shouldThrow() {
        OrderId orderId = OrderId.generate();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.cancel(
            new CancelOrderCommand(orderId, new CancellationReason("R"))))
            .isInstanceOf(OrderNotFoundException.class);

        then(orderRepository).should(never()).save(any());
        then(eventPublisher).shouldHaveNoInteractions();
    }
}
```

**체크리스트**:
- [ ] Spring 을 띄우지 않는가? (`@SpringBootTest` 금지)
- [ ] Repository / Port 만 Mock 이고, 그 외 도메인 객체는 실제인가?
- [ ] `Clock` 을 고정 시간으로 주입했는가?
- [ ] 성공 케이스와 실패 케이스를 모두 검증했는가?
- [ ] Mock 에 대해 "호출되었는가" 뿐 아니라 "몇 번, 어떤 인자로" 검증했는가?

### 6.3 Infrastructure (JPA) 테스트

**어노테이션**: `@DataJpaTest`
**H2 또는 Testcontainers** 로 실제 DB 사용. Mapper 와 Repository 구현의 **쿼리 동작**을 검증.

```java
@DataJpaTest
@Import({OrderRepositoryImpl.class, OrderMapper.class})
class OrderRepositoryImplTest {

    @Autowired OrderRepositoryImpl repository;
    @Autowired TestEntityManager em;

    @Test
    @DisplayName("주문을 저장하고 다시 조회하면 동일한 도메인 객체가 반환된다")
    void saveAndFind_shouldReturnSameOrder() {
        // given
        Order order = Order.place(
            OrderId.generate(),
            new CustomerId("CUST-1"),
            List.of(OrderLine.of("PROD-1", 2, Money.of(10_000, "KRW"))),
            Instant.parse("2026-04-20T10:00:00Z"));

        // when
        repository.save(order);
        em.flush();
        em.clear();
        Order found = repository.findById(order.id()).orElseThrow();

        // then
        assertThat(found.id()).isEqualTo(order.id());
        assertThat(found.customerId()).isEqualTo(order.customerId());
        assertThat(found.lines()).hasSize(1);
    }
}
```

**체크리스트**:
- [ ] `@DataJpaTest` 만 사용했는가? (`@SpringBootTest` 금지)
- [ ] `TestEntityManager.flush()` + `clear()` 로 영속성 컨텍스트를 비운 후 조회했는가?
- [ ] 실제 DB 제약(not null, unique) 위반도 테스트했는가?
- [ ] Testcontainers 사용 시 BaseClass 로 공유해 시작 시간을 줄였는가?

### 6.4 Presentation (Controller) 테스트

**어노테이션**: `@WebMvcTest(OrderController.class)`
**Application Service 는 Mock**.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @MockitoBean CancelOrderService cancelOrderService;

    @Test
    @DisplayName("POST /orders/{id}/cancel 요청이 성공하면 200 을 반환한다")
    void cancel_shouldReturn200() throws Exception {
        // given
        willDoNothing().given(cancelOrderService).cancel(any());

        // when & then
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", "ORD-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(
                    new CancelOrderRequest("CUSTOMER_REQUEST", "사이즈 변경"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("ORD-1"))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("reason 이 비어있으면 400 을 반환한다")
    void cancel_whenReasonBlank_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", "ORD-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new CancelOrderRequest("", null))))
            .andExpect(status().isBadRequest());
    }
}
```

**체크리스트**:
- [ ] `@WebMvcTest` 사용 (전체 컨텍스트 로딩 금지)
- [ ] Application Service 는 `@MockitoBean` 으로 Mock
- [ ] HTTP 상태 코드, 응답 바디 JSON 필드를 모두 검증
- [ ] 입력 검증 실패(400), 권한 실패(401/403) 도 검증

### 6.5 통합 테스트 (E2E)

**어노테이션**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc`
**최소한의 개수만** 작성. 크리티컬 플로우 (결제, 주문 생성) 정도.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderCancellationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;

    @Test
    @DisplayName("주문 생성 → 취소 전체 플로우")
    void fullFlow() throws Exception {
        // 주문 생성 API 호출
        // 주문 취소 API 호출
        // DB 상태 확인
    }
}
```

**체크리스트**:
- [ ] 단위/슬라이스 테스트로 커버되지 않는 **통합 지점** 만 검증하는가?
- [ ] 개수가 10개 이하인가? (너무 많으면 빌드가 느려짐)
- [ ] 외부 시스템은 Testcontainers 또는 WireMock 으로 대체했는가?

---

## 7. Fixture 관리

### 7.1 Test Object Mother 패턴

도메인 객체 생성이 복잡하면 별도 빌더를 둔다.

```java
// src/test/java/.../fixture/OrderFixtures.java
public final class OrderFixtures {

    private OrderFixtures() {}

    public static OrderBuilder anOrder() {
        return new OrderBuilder();
    }

    public static class OrderBuilder {
        private OrderId id = OrderId.generate();
        private CustomerId customerId = new CustomerId("CUST-" + UUID.randomUUID());
        private List<OrderLine> lines = List.of(
            OrderLine.of("PROD-1", 1, Money.of(10_000, "KRW")));
        private OrderStatus status = OrderStatus.PLACED;
        private Instant placedAt = Instant.parse("2026-04-20T10:00:00Z");

        public OrderBuilder withStatus(OrderStatus status) { this.status = status; return this; }
        public OrderBuilder withLines(List<OrderLine> lines) { this.lines = lines; return this; }
        // ...

        public Order build() {
            Order order = Order.place(id, customerId, lines, placedAt);
            if (status == OrderStatus.CANCELLED) {
                order.cancel(new CancellationReason("TEST"), placedAt.plusSeconds(60));
            }
            // 다른 상태도 동일하게 처리
            return order;
        }
    }
}
```

### 7.2 @ParameterizedTest 활용

유사한 케이스가 많으면 파라미터화한다.

```java
@ParameterizedTest(name = "{0} 상태의 주문은 취소 시 예외가 발생한다")
@EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELLED"})
void cancel_whenStatusNotCancellable_shouldThrow(OrderStatus status) {
    Order order = anOrder().withStatus(status).build();
    assertThatThrownBy(() -> order.cancel(new CancellationReason("R"), Instant.now()))
        .isInstanceOf(RuntimeException.class);
}
```

---

## 8. AssertJ 사용 권장

- `assertThat(...)` 로 일관성 유지
- 체인 방식으로 여러 속성 한꺼번에 검증 가능
- 컬렉션 검증이 강력함

```java
// 추천
assertThat(order.lines())
    .hasSize(2)
    .extracting(OrderLine::productId)
    .containsExactly("PROD-1", "PROD-2");

// 지양 (JUnit assert)
assertEquals(2, order.lines().size());
```

---

## 9. 테스트 커버리지 기준

- **Domain 계층**: 분기 커버리지 **95% 이상**
- **Application 계층**: 라인 커버리지 **90% 이상**
- **Infrastructure (Repository)**: 정상 / 예외 케이스 **각 1개 이상**
- **Presentation (Controller)**: 성공, 검증 실패, 예외 매핑 **각 1개 이상**
- **전체 프로젝트**: 라인 커버리지 **80% 이상** (JaCoCo 기준)

커버리지 숫자보다 **의미 있는 분기를 놓치지 않는 것**이 중요하다.

---

## 10. 안티 패턴 (즉시 교정)

| 안티 패턴 | 올바른 방법 |
|---|---|
| `@SpringBootTest` 를 모든 테스트에 사용 | 슬라이스 테스트 우선, E2E 는 최소화 |
| Value Object를 Mock | 실제 객체로 생성 |
| `LocalDateTime.now()` 직접 호출 | `Clock` 주입, `Clock.fixed` 사용 |
| `Thread.sleep()` 으로 타이밍 대기 | Awaitility 또는 이벤트 기반 검증 |
| `@Disabled` 붙이고 PR | 수정하거나 삭제 |
| 여러 assertion 을 복수 줄로 나열 | AssertJ 체인 또는 `assertAll` |
| 테스트 간 순서 의존 (`@Order`) | 각 테스트는 독립 실행 가능해야 함 |
| 프로덕션 코드를 테스트 전용으로 수정 (getter 추가 등) | 테스트 패키지에 헬퍼 또는 Fixture |

---

## 11. 실행 및 검증 명령

```bash
# 전체 테스트
./gradlew test

# 계층별 실행
./gradlew test --tests '*domain*'
./gradlew test --tests '*application*'
./gradlew test --tests '*infrastructure*'
./gradlew test --tests '*presentation*'

# 커버리지 리포트
./gradlew test jacocoTestReport
# → build/reports/jacoco/test/html/index.html
```

모든 테스트가 통과하고 커버리지가 기준을 만족한 후에만 다음 단계로 진행한다.

---

## 12. 다음 단계

테스트가 완성되면:
- 문서화 → `.claude/skills/documentation/SKILL.md`
- 커밋 → `.claude/skills/commit-convention/SKILL.md`

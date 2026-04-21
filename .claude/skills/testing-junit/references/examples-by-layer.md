# 계층별 테스트 상세 예시

---

## 1. Domain 테스트

**어노테이션**: 없음. 순수 JUnit + AssertJ. 테스트당 10ms 이하 목표.

```java
// src/test/java/.../domain/model/order/OrderTest.java
class OrderTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-04-20T10:00:00Z");

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
        @DisplayName("PLACED 상태의 주문은 취소된다")
        void cancel_whenPlaced_shouldSucceed() {
            Order order = anOrder().withStatus(OrderStatus.PLACED).build();

            order.cancel(new CancellationReason("REASON"), FIXED_NOW);

            assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.cancelledAt()).isEqualTo(FIXED_NOW);
        }

        @ParameterizedTest(name = "{0} 상태는 취소 시 예외가 발생한다")
        @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED"})
        void cancel_whenShippedOrDelivered_shouldThrow(OrderStatus status) {
            Order order = anOrder().withStatus(status).build();

            assertThatThrownBy(() -> order.cancel(new CancellationReason("R"), FIXED_NOW))
                .isInstanceOf(OrderCannotBeCancelledException.class);
        }
    }
}
```

---

## 2. Application Service 테스트

**어노테이션**: `@ExtendWith(MockitoExtension.class)`. Repository/외부 연동만 Mock.

```java
@ExtendWith(MockitoExtension.class)
class CancelOrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ApplicationEventPublisher eventPublisher;
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
        then(eventPublisher).should().publishEvent(any(OrderCancelled.class));
    }

    @Test
    @DisplayName("존재하지 않는 주문 취소 시 저장과 이벤트 발행이 일어나지 않는다")
    void cancel_whenOrderNotFound_shouldThrowAndNotSave() {
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

---

## 3. Infrastructure (JPA) 테스트

```java
@DataJpaTest
@Import({OrderRepositoryImpl.class, OrderMapper.class})
class OrderRepositoryImplTest {

    @Autowired OrderRepositoryImpl repository;
    @Autowired TestEntityManager em;

    @Test
    @DisplayName("주문을 저장하고 조회하면 동일한 도메인 객체가 반환된다")
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
        em.clear();                              // 1차 캐시 비움 — 실제 DB 왕복 검증
        Order found = repository.findById(order.id()).orElseThrow();

        // then
        assertThat(found.id()).isEqualTo(order.id());
        assertThat(found.lines()).hasSize(1);
    }
}
```

---

## 4. Presentation (Controller) 테스트

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @MockitoBean CancelOrderService cancelOrderService;

    @Test
    @DisplayName("POST /orders/{id}/cancel 요청이 성공하면 200을 반환한다")
    void cancel_shouldReturn200() throws Exception {
        willDoNothing().given(cancelOrderService).cancel(any());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", "ORD-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(
                    new CancelOrderRequest("CUSTOMER_REQUEST", "사이즈 변경"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value("ORD-1"))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("reason이 비어있으면 400을 반환한다")
    void cancel_whenReasonBlank_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", "ORD-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(new CancelOrderRequest("", null))))
            .andExpect(status().isBadRequest());
    }
}
```

---

## 5. 통합 테스트 (최소화)

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
        // 주문 생성 API → 취소 API → DB 상태 확인 → 이벤트 발행 확인
    }
}
```

**개수 제한**: 프로젝트 전체 10개 이하 권장. 슬라이스 테스트로 커버되지 않는 **통합 지점**만.

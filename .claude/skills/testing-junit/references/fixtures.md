# 테스트 Fixture 패턴

---

## Test Object Mother (권장)

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

        public OrderBuilder withId(OrderId id) { this.id = id; return this; }
        public OrderBuilder withStatus(OrderStatus status) { this.status = status; return this; }
        public OrderBuilder withLines(List<OrderLine> lines) { this.lines = lines; return this; }

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

**사용**:
```java
Order order = anOrder().withStatus(OrderStatus.CANCELLED).build();
```

---

## @ParameterizedTest

유사한 케이스가 많으면 파라미터화.

```java
@ParameterizedTest(name = "{0} 상태는 취소 시 예외가 발생한다")
@EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELLED"})
void cancel_whenStatusNotCancellable_shouldThrow(OrderStatus status) {
    Order order = anOrder().withStatus(status).build();

    assertThatThrownBy(() -> order.cancel(new CancellationReason("R"), Instant.now()))
        .isInstanceOf(RuntimeException.class);
}
```

---

## AssertJ Chain

```java
// 여러 속성을 한꺼번에 검증
assertThat(order.lines())
    .hasSize(2)
    .extracting(OrderLine::productId)
    .containsExactly("PROD-1", "PROD-2");

// 객체 필드를 soft assertion으로
assertThat(order)
    .returns(OrderStatus.CANCELLED, Order::status)
    .returns(FIXED_NOW, Order::cancelledAt);
```

---

## Clock 고정 패턴

```java
// 고정 시간
Clock fixedClock = Clock.fixed(Instant.parse("2026-04-20T10:00:00Z"), ZoneOffset.UTC);

// 가변 (테스트 중 시간 진행 시뮬레이션)
MutableClock mutableClock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
// ... 사용 후
mutableClock.advance(Duration.ofMinutes(5));
```

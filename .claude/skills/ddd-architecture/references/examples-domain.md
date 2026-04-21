# Domain 계층 코드 예시

SKILL.md 의 보조 문서. 필요할 때만 읽는다.

---

## Aggregate Root

```java
// domain/model/order/Order.java
package com.example.project.domain.model.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderLine> lines;
    private final Instant placedAt;
    private Instant cancelledAt;
    private CancellationReason cancellationReason;

    private Order(OrderId id, CustomerId customerId, List<OrderLine> lines, Instant placedAt) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("주문은 최소 1개 이상의 주문 품목을 포함해야 합니다.");
        }
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.PLACED;
        this.lines = new ArrayList<>(lines);
        this.placedAt = placedAt;
    }

    /** 신규 주문 생성 (외부 진입점) */
    public static Order place(OrderId id, CustomerId customerId, List<OrderLine> lines, Instant now) {
        return new Order(id, customerId, lines, now);
    }

    /** DB에서 복원 (불변식 검증 스킵) */
    public static Order reconstitute(OrderId id, CustomerId customerId, OrderStatus status,
                                     List<OrderLine> lines, Instant placedAt,
                                     Instant cancelledAt, CancellationReason cancellationReason) {
        Order order = new Order(id, customerId, lines, placedAt);
        order.status = status;
        order.cancelledAt = cancelledAt;
        order.cancellationReason = cancellationReason;
        return order;
    }

    public void cancel(CancellationReason reason, Instant now) {
        if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
            throw new OrderCannotBeCancelledException(this.id, this.status);
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(this.id);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = now;
    }

    public OrderId id() { return id; }
    public OrderStatus status() { return status; }
    public List<OrderLine> lines() { return Collections.unmodifiableList(lines); }
    public Instant cancelledAt() { return cancelledAt; }
    public CancellationReason cancellationReason() { return cancellationReason; }
}
```

---

## Value Object

```java
// domain/model/order/OrderId.java
public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId must not be blank");
        }
    }

    public static OrderId generate() {
        return new OrderId(java.util.UUID.randomUUID().toString());
    }
}
```

```java
// domain/model/order/Money.java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount == null || currency == null) {
            throw new IllegalArgumentException("Money requires amount and currency");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currencyCode));
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

---

## Repository 인터페이스

```java
// domain/repository/OrderRepository.java
package com.example.project.domain.repository;

import com.example.project.domain.model.order.Order;
import com.example.project.domain.model.order.OrderId;
import java.util.Optional;

public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    Order save(Order order);
    void delete(OrderId id);
}
```

---

## Domain Event

```java
// domain/event/OrderCancelled.java
public record OrderCancelled(
    OrderId orderId,
    CustomerId customerId,
    CancellationReason reason,
    Instant occurredAt
) implements DomainEvent {}
```

---

## 외부 시스템 연동 인터페이스 (Domain에 정의)

외부 API 호출이 필요하면 **Domain이 이름을 정한다**. Infrastructure가 구현 세부를 채운다.

```java
// domain/service/PaymentGateway.java
// (도메인이 외부 결제 시스템을 부를 때 필요로 하는 계약)
public interface PaymentGateway {
    RefundResult refund(OrderId orderId, Money amount);
}
```

> Infrastructure는 이 인터페이스를 구현하는 `TossPaymentGateway` 등을 둔다.
> Domain은 "환불할 수 있다"만 알고, "어떤 PG 사인지"는 모른다.

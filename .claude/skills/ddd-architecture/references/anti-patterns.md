# DDD 안티패턴 모음

SKILL.md 의 보조 문서. 코드 리뷰 시 빠르게 체크할 용도.

---

## AP1. Anemic Domain Model

```java
// ❌ Order에 로직이 없고, 모두 OrderService에 있음
public class Order {
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setCancelledAt(Instant at) { this.cancelledAt = at; }
}

public class OrderService {
    public void cancel(Long id) {
        Order o = repo.findById(id);
        if (o.getStatus() == SHIPPED) throw new ...;   // 로직이 서비스에 샘
        o.setStatus(CANCELLED);
        o.setCancelledAt(LocalDateTime.now());
    }
}

// ✅ Order가 자기 책임을 가짐
public class Order {
    public void cancel(CancellationReason reason, Instant now) {
        if (this.status == SHIPPED) throw new OrderCannotBeCancelledException(...);
        this.status = CANCELLED;
        this.cancelledAt = now;
    }
}
```

---

## AP2. Primitive Obsession

```java
// ❌ 의미가 드러나지 않고, 잘못된 ID를 섞어 쓸 위험
public class Order {
    private Long id;
    private Long customerId;
    private BigDecimal totalAmount;
}

// ✅ 각 개념이 VO
public class Order {
    private OrderId id;
    private CustomerId customerId;
    private Money total;
}
```

---

## AP3. Leaky Aggregate

```java
// ❌ 외부에서 내부 Collection을 직접 수정 가능
public class Order {
    private List<OrderLine> lines;
    public List<OrderLine> getLines() { return lines; }  // 그대로 노출
}

// 호출부
order.getLines().add(newLine);  // Order의 불변식 우회

// ✅ 캡슐화 + 불변 뷰
public class Order {
    public void addLine(ProductId productId, int quantity, Money unitPrice) {
        if (this.status != PLACED) throw new IllegalStateException(...);
        this.lines.add(new OrderLine(productId, quantity, unitPrice));
    }
    public List<OrderLine> lines() { return Collections.unmodifiableList(lines); }
}
```

---

## AP4. Giant Aggregate

```java
// ❌ Customer가 Order 수천 개를 List로 소유
public class Customer {
    private List<Order> orders;   // 조회/수정 비용 폭발
}

// ✅ Customer는 ID만, Order는 별도 Aggregate Root
public class Customer {
    private CustomerId id;
    // Order 목록은 OrderRepository.findByCustomerId(...) 로 조회
}
```

---

## AP5. Missing Invariant Enforcement

```java
// ❌ "재고는 음수가 될 수 없다" 가 서비스 if문에만 존재
public class InventoryService {
    public void decrease(Long productId, int amount) {
        Stock stock = repo.findById(productId);
        if (stock.getQuantity() - amount < 0) throw new ...;  // 여기만 검증
        stock.setQuantity(stock.getQuantity() - amount);
    }
}

// ✅ Stock 내부에서 불변식 보장
public class Stock {
    public void decrease(int amount) {
        if (this.quantity < amount) throw new InsufficientStockException(...);
        this.quantity -= amount;
    }
}
```

---

## AP6. Non-Domain Event Name

```java
// ❌ 기술적/상태 나열식 이름
public class OrderStatusChangedEvent { ... }
public class OrderUpdatedEvent { ... }

// ✅ 도메인 사건의 이름
public class OrderCancelled { ... }
public class OrderShipped { ... }
public class OrderDelivered { ... }
```

---

## AP7. Cross-Aggregate Transaction

```java
// ❌ 한 트랜잭션에서 여러 AR을 수정
@Transactional
public void transfer(AccountId from, AccountId to, Money amount) {
    Account src = accountRepo.findById(from);
    Account dst = accountRepo.findById(to);
    src.withdraw(amount);
    dst.deposit(amount);
    accountRepo.save(src);
    accountRepo.save(dst);       // 두 AR 동시 수정 = 락 경합, 확장성 저하
}

// ✅ 한 트랜잭션 = 한 AR. 다른 AR은 이벤트로 최종 일관성
@Transactional
public void withdraw(AccountId from, Money amount, AccountId to) {
    Account src = accountRepo.findById(from);
    src.withdraw(amount);
    accountRepo.save(src);
    eventPublisher.publishEvent(new MoneyWithdrawn(from, to, amount));
}

// dst 는 MoneyWithdrawn 이벤트를 구독해 별도 트랜잭션에서 처리
```

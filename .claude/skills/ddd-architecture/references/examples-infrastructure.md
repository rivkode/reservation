# Infrastructure 계층 코드 예시

---

## JPA Entity

```java
// infrastructure/persistence/entity/OrderJpaEntity.java
package com.example.project.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatusJpa status;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "order")
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    protected OrderJpaEntity() {} // JPA 전용

    public OrderJpaEntity(String id, String customerId, OrderStatusJpa status, Instant placedAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.placedAt = placedAt;
    }

    // getter/setter (package-private 권장)
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatusJpa getStatus() { return status; }
    public Instant getPlacedAt() { return placedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public List<OrderLineJpaEntity> getLines() { return lines; }

    void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    void setCancellationReason(String reason) { this.cancellationReason = reason; }
}
```

**포인트**: 비즈니스 메서드 없음. 생성자는 필수 필드만. setter는 최소.

---

## Mapper

```java
// infrastructure/persistence/mapper/OrderMapper.java
@Component
public class OrderMapper {

    public OrderJpaEntity toJpaEntity(Order domain) {
        OrderJpaEntity entity = new OrderJpaEntity(
            domain.id().value(),
            domain.customerId().value(),
            OrderStatusJpa.valueOf(domain.status().name()),
            domain.placedAt()
        );
        domain.lines().forEach(line -> entity.getLines().add(toLineEntity(line, entity)));
        entity.setCancelledAt(domain.cancelledAt());
        entity.setCancellationReason(
            domain.cancellationReason() != null ? domain.cancellationReason().code() : null);
        return entity;
    }

    public Order toDomain(OrderJpaEntity entity) {
        return Order.reconstitute(
            new OrderId(entity.getId()),
            new CustomerId(entity.getCustomerId()),
            OrderStatus.valueOf(entity.getStatus().name()),
            entity.getLines().stream().map(this::toLineDomain).toList(),
            entity.getPlacedAt(),
            entity.getCancelledAt(),
            entity.getCancellationReason() != null
                ? new CancellationReason(entity.getCancellationReason())
                : null
        );
    }
}
```

**핵심**: DB에서 복원할 때는 `Order.reconstitute(...)` 사용 — 불변식 검증을 건너뛰어 저장된 상태 그대로 복원.

---

## Repository 구현

```java
// infrastructure/persistence/repository/OrderRepositoryImpl.java
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;
    private final OrderMapper mapper;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository, OrderMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public void delete(OrderId id) {
        jpaRepository.deleteById(id.value());
    }
}

// Spring Data JPA 인터페이스는 별도 (package-private)
interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {}
```

**핵심**: `OrderJpaRepository` 는 package-private — 외부 계층에서 보이지 않음.

---

## 외부 시스템 연동 구현

Domain에 정의된 인터페이스(`PaymentGateway`)를 Infrastructure가 구현한다.

```java
// infrastructure/external/TossPaymentGateway.java
@Component
public class TossPaymentGateway implements PaymentGateway {

    private final RestClient restClient;

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public RefundResult refund(OrderId orderId, Money amount) {
        try {
            TossRefundResponse response = restClient.post()
                .uri("/v1/payments/refund")
                .body(new TossRefundRequest(orderId.value(), amount.amount()))
                .retrieve()
                .body(TossRefundResponse.class);
            return RefundResult.success(response.refundId());
        } catch (RestClientException e) {
            return RefundResult.failure(e.getMessage());
        }
    }
}
```

**핵심**: 네트워크/재시도/오류 처리는 Infrastructure 내부에서. Domain은 `RefundResult` 만 받는다.

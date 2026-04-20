---
name: ddd-architecture
description: Java/Spring 프로젝트에서 DDD(Domain-Driven Design) 원칙을 적용해 코드를 작성/수정할 때 사용하는 스킬. 도메인 객체와 JPA Entity 분리, Aggregate/Entity/Value Object 설계, Repository 인터페이스와 구현 분리, 계층간 의존성 방향, 매퍼 패턴, 도메인 이벤트 등을 다룬다. "도메인 모델링", "엔티티 추가", "서비스 구현", "Repository 작성", "Aggregate", "Value Object" 같은 키워드가 나오거나 코드 계획(code-planning) 단계가 완료된 직후에 반드시 사용한다. Controller/Service/Entity 같은 단어만 나와도 이 스킬로 설계 원칙을 먼저 확인한다.
---

# DDD Architecture - Java/Spring DDD 구현 가이드

이 스킬은 계획 단계(`code-planning`) 이후 실제 구현 시 참조합니다.
**핵심 원칙**: 도메인은 프레임워크로부터 자유로워야 한다.

---

## 1. 계층 구조와 의존성 방향

```
┌──────────────────────────────────────────────────────┐
│  Presentation (Controller, Request/Response DTO)      │
│    │                                                   │
│    ▼                                                   │
│  Application (Use Case, Command/Query)           │
│    │                                                   │
│    ▼                                                   │
│  Domain (Aggregate, Entity, VO, Domain Service, Event) │
│    ▲                                                   │
│    │                                                   │
│  Infrastructure (JpaEntity, Repository 구현, Mapper)   │
└──────────────────────────────────────────────────────┘
```

**의존성 규칙**:
- Domain 은 **어떤 계층에도 의존하지 않는다**.
- Application 은 Domain 에만 의존한다 (Spring 의존은 최소화).
- Infrastructure 는 Domain 의 인터페이스를 구현한다 (의존성 역전).
- Presentation 은 Application 에 의존한다.

**금지**:
- ❌ Controller → Repository 직접 호출
- ❌ Domain → JpaEntity / Spring / Jackson
- ❌ Application → Controller / Presentation DTO
- ❌ JpaEntity → Domain 객체
- ❌ Port, adapter 개념과 같이 Input, Output 개념 금지. Dto 로 관리

---

## 2. Domain 계층 작성 규칙

### 2.1 도메인 객체는 순수 Java

```java
// ✅ 올바른 예시: domain/model/order/Order.java
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

    public static Order place(OrderId id, CustomerId customerId, List<OrderLine> lines, Instant now) {
        return new Order(id, customerId, lines, now);
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
    // ... 기타 접근자. setter 는 만들지 않는다.
}
```

**규칙**:
- `@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue` 등 JPA 어노테이션 **절대 금지**
- `@Component`, `@Service` 등 Spring 어노테이션 **절대 금지**
- public setter 금지 (상태 변경은 의미 있는 메서드로: `cancel()`, `approve()` 등)
- 생성은 `static` 팩토리 메서드 (`place`, `create`, `from`) 사용
- 불변식(invariant)은 생성자/메서드에서 즉시 검증
- ID 는 `Long` 이 아닌 Value Object (`OrderId`) 로 래핑

### 2.2 Value Object

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

- `record` 또는 불변 클래스로 작성
- equals/hashCode 는 값 기반 (record 가 자동 제공)
- 생성 시점에 유효성 검증

### 2.3 Repository 인터페이스

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

- **인터페이스만** Domain 에 둔다.
- 구현체는 Infrastructure 계층의 `OrderRepositoryImpl` 에 위치.
- 반환 타입은 Domain 객체여야 하며, JpaEntity 를 노출하지 않는다.

### 2.4 Domain Service

여러 Aggregate 에 걸친 로직이 있을 때만 사용한다. Aggregate 하나로 끝나는 로직은 Aggregate 안에 두는 것이 우선.

```java
// domain/service/OrderPricingPolicy.java
public class OrderPricingPolicy {
    public Money calculateTotal(Order order, DiscountPolicy discountPolicy) {
        // ...
    }
}
```

### 2.5 Domain Event

```java
// domain/event/OrderCancelled.java
public record OrderCancelled(
    OrderId orderId,
    CancellationReason reason,
    Instant occurredAt
) implements DomainEvent {}
```

이벤트는 Aggregate 내부에서 수집하고, Application Service 에서 발행한다.

---

## 3. Application 계층 작성 규칙

### 3.1 Application Service

```java
// application/service/CancelOrderService.java
package com.example.project.application.service;

import com.example.project.application.dto.CancelOrderCommand;
import com.example.project.domain.model.order.*;
import com.example.project.domain.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CancelOrderService {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public CancelOrderService(
            OrderRepository orderRepository,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void cancel(CancelOrderCommand command) {
        Instant now = Instant.now(clock);
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        order.cancel(command.reason(), now);
        orderRepository.save(order);

        eventPublisher.publish(new OrderCancelled(order.id(), command.reason(), now));
    }
}
```

**규칙**:
- `@Service`, `@Transactional` 은 여기에만 붙는다.
- 트랜잭션 경계는 Application Service 메서드 단위.
- Command / Query 객체로 입력을 받는다 (원시 타입 여러 개 나열 금지).
- 시간은 `Clock` 주입받아 `Instant.now(clock)` 으로 얻는다 (테스트 가능성).
- Application Service 는 **얇게** 유지한다: 도메인 객체를 조립하고, 로직은 도메인에 위임.

### 3.2 Command / Query 분리 (CQS)

```java
// application/dto/CancelOrderCommand.java
public record CancelOrderCommand(
    OrderId orderId,
    CancellationReason reason
) {}
```

- 조회 전용 메서드는 `@Transactional(readOnly = true)` 의 별도 Query Service 로 분리 가능.

### 3.3 Port (외부 시스템 호출)

외부 API 호출이 필요하면 Application 에 Port(인터페이스) 를 정의하고 Infrastructure 가 구현한다.

```java
// application/port/PaymentGatewayPort.java
public interface PaymentGatewayPort {
    RefundResult refund(OrderId orderId, Money amount);
}
```

---

## 4. Infrastructure 계층 작성 규칙

### 4.1 JPA Entity (DB Entity)

```java
// infrastructure/persistence/entity/OrderJpaEntity.java
package com.example.project.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

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

    // 생성자, getter/setter (패키지 가시성 권장)
}
```

**규칙**:
- 클래스명은 `XxxJpaEntity` 로 끝낸다 (Domain 과 이름 충돌 방지).
- Domain 객체의 복사가 아닌, **DB 구조에 최적화된** 형태로 설계 가능.
- `protected` no-arg 생성자 (JPA 요구).
- Domain 비즈니스 메서드를 여기에 넣지 않는다.

### 4.2 Mapper (Domain ↔ JpaEntity)

```java
// infrastructure/persistence/mapper/OrderMapper.java
package com.example.project.infrastructure.persistence.mapper;

@Component
public class OrderMapper {

    public OrderJpaEntity toJpaEntity(Order domain) {
        OrderJpaEntity entity = new OrderJpaEntity(
            domain.id().value(),
            domain.customerId().value(),
            OrderStatusJpa.valueOf(domain.status().name()),
            domain.placedAt()
        );
        domain.lines().forEach(line -> entity.addLine(toLineEntity(line)));
        entity.setCancelledAt(domain.cancelledAt());
        entity.setCancellationReason(domain.cancellationReason() != null
            ? domain.cancellationReason().code() : null);
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
                ? new CancellationReason(entity.getCancellationReason()) : null
        );
    }
}
```

**팁**:
- DB 에서 복원할 때는 `Order.reconstitute(...)` 같은 별도 팩토리 메서드를 사용한다 (불변식 검증을 건너뛰는 용도).
- MapStruct 를 사용해도 되나, **자동 생성 코드가 Domain 규칙을 훼손하지 않는지** 확인할 것.

### 4.3 Repository 구현

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
}

// Spring Data JPA 인터페이스는 별도
interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {}
```

---

## 5. Presentation 계층 작성 규칙

### 5.1 Controller

```java
// presentation/controller/OrderController.java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CancelOrderService cancelOrderService;

    public OrderController(CancelOrderService cancelOrderService) {
        this.cancelOrderService = cancelOrderService;
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public CancelOrderResponse cancel(
            @PathVariable String orderId,
            @Valid @RequestBody CancelOrderRequest request) {
        cancelOrderService.cancel(new CancelOrderCommand(
            new OrderId(orderId),
            new CancellationReason(request.reason())
        ));
        return new CancelOrderResponse(orderId, "CANCELLED");
    }
}
```

**규칙**:
- Controller 는 **얇게**: 입력 검증, DTO 변환, Application 호출만.
- Presentation DTO 와 Application Command 를 분리한다.
- 예외는 `@RestControllerAdvice` 에서 공통 처리.

### 5.2 Request / Response DTO

```java
// presentation/dto/CancelOrderRequest.java
public record CancelOrderRequest(
    @NotBlank String reason,
    String detail
) {}
```

- 입력 검증(`@NotBlank`, `@Email`) 어노테이션은 여기에서만.
- Domain 객체를 그대로 응답으로 돌려주지 않는다.

---

## 6. 패키지별 자가 검증 체크리스트

구현 후 각 파일에 대해 아래를 점검한다.

### Domain 파일 체크리스트
- [ ] JPA/Spring 어노테이션이 없는가?
- [ ] public setter 가 없는가?
- [ ] 생성자에서 불변식을 검증하는가?
- [ ] ID 가 Value Object 로 래핑되어 있는가?
- [ ] 상태 변경 메서드가 의미 있는 이름(`cancel`, `approve`)인가?
- [ ] 외부 라이브러리 import 가 `java.*` 와 프로젝트 내부 패키지뿐인가?

### Application 파일 체크리스트
- [ ] `@Transactional` 경계가 명확한가?
- [ ] Command/Query 객체로 입력받는가?
- [ ] `Clock` 을 주입받아 시간을 처리하는가?
- [ ] Domain 객체를 Presentation 으로 그대로 노출하지 않는가?

### Infrastructure 파일 체크리스트
- [ ] JpaEntity 클래스명이 `XxxJpaEntity` 인가?
- [ ] Mapper 가 Domain ↔ JpaEntity 양방향 변환을 제공하는가?
- [ ] Spring Data JPA 인터페이스가 Domain 에 노출되지 않는가?

### Presentation 파일 체크리스트
- [ ] Controller 가 Repository 를 직접 주입받지 않는가?
- [ ] Request DTO 에 입력 검증 어노테이션이 있는가?
- [ ] Domain 객체를 Response 로 그대로 반환하지 않는가?

---

## 7. 자주 나오는 설계 판단

### Q1. 어떤 것이 Aggregate Root 인가?
- 외부에서 참조할 수 있는 단위가 Aggregate Root.
- 트랜잭션 단위이기도 하다.
- 예: `Order` 는 AR, `OrderLine` 은 내부 Entity (외부에서 직접 접근 금지).

### Q2. Value Object 로 만들어야 할지, Entity 로 만들어야 할지?
- **정체성(identity)** 이 중요하면 Entity. 예: `Order`, `Customer`.
- 값 자체가 의미면 VO. 예: `Money`, `Email`, `Address`.
- 애매하면 VO 로 시작 (변경이 쉬움).

### Q3. 도메인 로직을 Service 에 둬야 할지, Aggregate 에 둬야 할지?
- 하나의 Aggregate 안에서 완결되면 → Aggregate 메서드.
- 여러 Aggregate 에 걸치면 → Domain Service.
- 트랜잭션, 외부 호출, 로깅이 필요하면 → Application Service.

### Q4. 이벤트를 언제 발행하는가?
- Aggregate 내부에서 수집 → Application Service 에서 `save()` 후 발행.
- 또는 Spring 의 `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`.

---

## 8. 안티 패턴 (즉시 교정)

| 안티 패턴 | 올바른 방법 |
|---|---|
| Domain 에 `@Entity` 부착 | JpaEntity 로 분리 + Mapper |
| `@Data` / public setter 남발 | static 팩토리 + 의미 있는 메서드 |
| Controller 에서 `OrderRepository` 주입 | Application Service 경유 |
| `Long id` 원시 타입 ID | `OrderId` VO 로 래핑 |
| `LocalDateTime.now()` 호출 | `Clock` 주입 후 `Instant.now(clock)` |
| Application Service 에 if/else 비즈니스 규칙 | Domain 메서드로 이동 |
| Aggregate 내부 Entity 를 외부에서 직접 조작 | Aggregate Root 통해서만 접근 |

---

## 9. 다음 단계

구현이 끝나면:
- 테스트 코드 작성 → `.claude/skills/testing-junit/SKILL.md`
- 테스트가 계층별로 독립적으로 동작하는지 반드시 확인

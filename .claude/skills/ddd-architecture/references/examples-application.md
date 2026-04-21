# Application 계층 코드 예시

---

## Application Service

```java
// application/service/CancelOrderService.java
package com.example.project.application.service;

import com.example.project.application.dto.CancelOrderCommand;
import com.example.project.domain.event.OrderCancelled;
import com.example.project.domain.model.order.*;
import com.example.project.domain.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CancelOrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CancelOrderService(OrderRepository orderRepository,
                              ApplicationEventPublisher eventPublisher,
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

        eventPublisher.publishEvent(
            new OrderCancelled(order.id(), order.customerId(), command.reason(), now));
    }
}
```

---

## Command / Query 객체

```java
// application/dto/CancelOrderCommand.java
public record CancelOrderCommand(
    OrderId orderId,
    CancellationReason reason
) {}
```

```java
// application/dto/OrderQuery.java
public record OrderQuery(
    CustomerId customerId,
    OrderStatus status,
    int page,
    int size
) {}
```

---

## 조회 전용 Service

```java
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderView findById(OrderId id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderView.from(order);
    }
}
```

---

## 이벤트 리스너 (같은 계층 또는 Infrastructure)

```java
@Component
public class OrderCancelledListener {

    private final PaymentGateway paymentGateway;   // Domain 인터페이스
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCancelled event) {
        // 환불 처리. 실패해도 원 트랜잭션은 이미 커밋됨.
        paymentGateway.refund(event.orderId(), computeRefundAmount(event));
    }
}
```

---

## 왜 이렇게?

- `@Service` `@Transactional` 은 Application 에만. Domain 은 순수.
- `Clock` 주입 → 테스트에서 `Clock.fixed(...)` 로 시간 결정론 확보.
- 이벤트 발행은 `save()` 다음에. 실패 시 이벤트 발행 안 됨.
- 외부 호출(결제 환불)은 `AFTER_COMMIT` 리스너에서. 외부 장애가 주문 취소를 롤백시키지 않음.

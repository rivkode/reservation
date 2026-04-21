# 테스트 안티패턴 모음

코드 리뷰 시 빠르게 체크할 용도. 각각은 test-reviewer 에이전트의 탐지 대상.

---

## T1. Value Object Mocking (Critical)

```java
// ❌
@Mock Money money;
when(money.amount()).thenReturn(BigDecimal.TEN);

// ✅
Money money = Money.of(10, "KRW");
```

## T2. Aggregate Root Mocking (Critical)

```java
// ❌ 메서드 호출 여부만 검증, 실제 상태 변화 미검증
@Mock Order order;
service.cancel(command);
verify(order).cancel(any(), any());

// ✅
Order order = Order.place(...);
given(repository.findById(orderId)).willReturn(Optional.of(order));
service.cancel(command);
assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
```

## T3. 모든 것을 @SpringBootTest (High)

```java
// ❌
@SpringBootTest
class OrderTest { ... }  // 순수 도메인인데 전체 컨텍스트 로딩

// ✅
class OrderTest { ... }  // 순수 JUnit
```

## T4. Happy Path Only (High)

```java
// ❌
@Test void cancel_success() { ... }
// 끝.

// ✅
@Test void cancel_whenPlaced_shouldSucceed()
@Test void cancel_whenShipped_shouldThrow()
@Test void cancel_whenAlreadyCancelled_shouldThrow()
@Test void cancel_whenOrderNotFound_shouldThrow()
```

## T5. `LocalDateTime.now()` 직접 호출 (High - Flaky)

```java
// ❌
assertThat(order.cancelledAt()).isEqualTo(LocalDateTime.now());  // 거의 항상 실패

// ✅
Instant now = Instant.parse("2026-04-20T10:00:00Z");
// Clock.fixed(now, ZoneOffset.UTC) 주입
assertThat(order.cancelledAt()).isEqualTo(now);
```

## T6. Thread.sleep (High - Flaky)

```java
// ❌
eventPublisher.publish(event);
Thread.sleep(1000);
assertThat(handler.wasCalled()).isTrue();

// ✅ Awaitility
await().atMost(5, SECONDS).until(handler::wasCalled);
```

## T7. 무의미한 테스트 이름 (Medium)

```java
// ❌
@Test void test1() { ... }
@Test void shouldWork() { ... }

// ✅
@Test
@DisplayName("배송되지 않은 주문은 정상적으로 취소된다")
void cancel_whenOrderNotShipped_shouldMarkAsCancelled() { ... }
```

## T8. 과도한 verify() (Medium)

```java
// ❌ 상태 변화 확인 없이 Mock 호출만 검증
verify(repository).save(any());
verify(publisher).publish(any());

// ✅ 상태 + 상호작용 모두
assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
then(repository).should().save(order);
then(publisher).should().publishEvent(any(OrderCancelled.class));
```

## T9. @Disabled 방치 (Critical)

```java
// ❌
@Test
@Disabled("나중에 고침")
void cancel_...() { ... }
```
→ 수정하거나 삭제. "나중"은 없다.

## T10. 한 테스트에 여러 시나리오 (Medium)

```java
// ❌
@Test void cancelTest() {
    // given: 주문 생성
    // when-then: 취소
    // given: 다시 생성
    // when-then: 이미 취소된 주문 재취소 시도
}

// ✅ 시나리오별로 @Test 분리
```

## T11. JPA 테스트에 flush/clear 누락 (High)

```java
// ❌
@DataJpaTest
class OrderRepositoryTest {
    @Test void save() {
        repository.save(order);
        Order found = repository.findById(order.id()).get();  // 1차 캐시 히트
        assertThat(found).isEqualTo(order);  // 실제 DB 왕복 미검증
    }
}

// ✅
@Test void save() {
    repository.save(order);
    em.flush();
    em.clear();
    Order found = repository.findById(order.id()).get();
    ...
}
```

## T12. Controller 테스트에 @SpringBootTest (High)

```java
// ❌
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest { ... }

// ✅
@WebMvcTest(OrderController.class)
class OrderControllerTest { ... }
```

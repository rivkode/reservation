---
name: module-boundary
description: MSA 멀티모듈(모노레포) 환경에서 서비스간 경계, 통신, 공유 계약을 다룰 때 사용한다. 새로운 서비스간 통신(gRPC, Kafka 이벤트), contracts 모듈의 proto/이벤트 추가·수정, 서비스간 데이터 일관성(Saga), Circuit Breaker, 멱등성 처리, 모듈 의존성 검증, Redis Read Model 동기화를 포함한다. "다른 서비스 호출", "gRPC", "proto", "이벤트 발행", "Kafka", "Saga", "서비스간", "contracts", "공유 모듈", "캐시 동기화" 키워드가 나오거나 여러 서비스에 영향을 주는 기능을 작업할 때 code-planning 직후 반드시 사용. 서비스 경계 침범을 절대 허용하지 않는다.
---

# Module Boundary — 서비스간 경계와 통신

MSA 멀티모듈(모노레포)에서 **서비스 경계 침범을 방지**하고 **서비스간 통신의 일관성**을 보장하기 위한 스킬.

**기술 스택**: 동기 **gRPC**, 비동기 **Kafka**, **MySQL** (서비스별), **Redis** (조회 캐시)

---

## 1. 절대 원칙

1. **한 서비스 = 하나의 Bounded Context = 하나의 MySQL 스키마**
2. **다른 서비스의 Domain / JpaEntity / Repository 를 import 하지 않는다.**
3. **공유는 오직 `contracts` 모듈 (proto + Kafka 이벤트) 을 통해서만.**
4. **데이터 중복 허용.** 다른 서비스 데이터가 필요하면 복제하거나 호출하거나. JOIN 금지.
5. **서비스간 호출은 실패를 전제로 설계.** Deadline, Retry, Circuit Breaker 필수.
6. **읽기 캐시와 SoT 를 명확히 구분.** 쓰기는 SoT 서비스에서만.

---

## 2. 결정 트리 — 다른 서비스 데이터가 필요할 때

```
필요한 데이터가 다른 서비스에 있는가?
├── 실시간으로 정확한 최신값이 필수인가?
│   ├── 예 → 동기 gRPC 호출 + Deadline + Circuit Breaker
│   │        예: 예약 시 투숙객 유효성 확인
│   └── 아니오 → 비동기 이벤트 구독 + 로컬 복제 / Redis Read Model
│                예: hotel-service 가 ReservationCreated 구독 → 가용성 캐시 갱신
│
└── 내 서비스 내부에서 해결 가능한가? (데이터 복제 / 재설계)
    └── 가능하면 우선 고려. 서비스간 호출 자체를 줄임.
```

**기본 원칙**: **비동기 이벤트 > 동기 gRPC > 피하기** 순으로 선호.

---

## 3. `contracts` 모듈 사용법

### 3.1 구조

```
contracts/
├── build.gradle.kts                # protobuf-gradle-plugin 적용
└── src/main/
    ├── proto/                      # gRPC 서비스 정의
    │   ├── hotel.proto
    │   ├── rate.proto
    │   ├── guest.proto
    │   └── reservation.proto
    └── java/com/example/contracts/
        └── event/                  # Kafka 이벤트 (Java record)
            ├── hotel/
            │   ├── RoomCreated.java
            │   └── RoomUpdated.java
            ├── rate/
            │   └── RoomTypeRateChanged.java
            └── reservation/
                ├── ReservationCreated.java
                └── ReservationCancelled.java
```

### 3.2 절대 두지 않는 것

- ❌ Aggregate, Entity, Value Object (서비스 내부 도메인)
- ❌ JPA Entity
- ❌ 내부 구현 클래스
- ❌ Spring 어노테이션이 붙은 클래스
- ❌ 한 서비스만 쓰는 DTO

### 3.3 Proto 파일 작성 규칙

```protobuf
// contracts/src/main/proto/guest.proto
syntax = "proto3";

package com.example.contracts.proto.guest;

option java_multiple_files = true;
option java_package = "com.example.contracts.proto.guest";
option java_outer_classname = "GuestProto";

// guest-service 가 제공하는 서비스.
// 호출자: reservation-service (예약 생성 시 투숙객 검증)
service GuestService {
  rpc GetGuest (GetGuestRequest) returns (GuestResponse);
}

message GetGuestRequest {
  string guest_id = 1;
}

message GuestResponse {
  string guest_id = 1;
  string name = 2;
  string email = 3;
  bool active = 4;
}
```

**규칙**:
- 파일명: `<service>.proto`
- 패키지는 `com.example.contracts.proto.<service>`
- 주석으로 **제공자 / 호출자 / 용도** 명시
- field number 는 **한 번 할당하면 변경 금지** (wire 호환성)
- 새 필드는 새 번호로 추가, 삭제 필드는 `reserved` 로 영구 차단
- proto3 에서 `required` 사용 불가 (기본 optional-like)

### 3.4 Kafka 이벤트 작성 규칙

```java
// contracts/src/main/java/com/example/contracts/event/reservation/ReservationCreated.java
package com.example.contracts.event.reservation;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 예약이 성공적으로 생성되었을 때 reservation-service 가 발행하는 이벤트.
 *
 * <p>발행자: reservation-service
 * <p>구독자: hotel-service (가용성 캐시 갱신), rate-service (정산 레코드 생성), guest-service (방문 이력)
 * <p>토픽: {@code reservation-events}
 *
 * @since 1.0
 */
public record ReservationCreated(
    String eventId,              // 멱등성 키 (UUID)
    Instant occurredAt,
    String reservationId,
    String guestId,
    String hotelId,
    String roomTypeId,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int numberOfGuests
) {
    public ReservationCreated {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
    }
}
```

**규칙**:
- `record` 로 불변
- `eventId`, `occurredAt` 필수
- 순수 JDK 타입만 (Spring/Jackson 어노테이션 금지)
- JavaDoc 으로 **발행자 / 구독자 / 토픽 / 용도** 명시
- Breaking Change 시 버전 분리 (`ReservationCreatedV2`)

---

## 4. 비동기 이벤트 (Kafka)

### 4.1 발행 측 (Producer) — Outbox 패턴

DB 트랜잭션과 이벤트 발행의 원자성 보장.

```java
// <service>/.../infrastructure/messaging/OutboxEventPublisher.java
@Component
public class OutboxEventPublisher {

    private final OutboxJpaRepository outboxRepo;
    private final ObjectMapper objectMapper;

    /** Application Service 에서 호출. DB 트랜잭션 안에서 Outbox 테이블에 저장. */
    public void publish(Object event, String topic) {
        try {
            OutboxEntryJpaEntity entry = new OutboxEntryJpaEntity(
                UUID.randomUUID().toString(),
                topic,
                event.getClass().getName(),
                objectMapper.writeValueAsString(event),
                Instant.now()
            );
            outboxRepo.save(entry);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(e);
        }
    }
}

// 별도 스케줄러(@Scheduled)가 Outbox → Kafka 로 전달 (at-least-once)
```

**MySQL Outbox 테이블** (Flyway):
```sql
CREATE TABLE outbox_entries (
    id          VARCHAR(36) PRIMARY KEY,
    topic       VARCHAR(100) NOT NULL,
    event_type  VARCHAR(255) NOT NULL,
    payload     JSON NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    INDEX idx_outbox_unpublished (published_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 4.2 구독 측 (Consumer) — 멱등성 필수

```java
// hotel-service/.../infrastructure/messaging/ReservationCreatedListener.java
import com.example.contracts.event.reservation.ReservationCreated;

@Component
public class ReservationCreatedListener {

    private final ProcessedEventRepository processedEventRepo;
    private final RoomAvailabilityCacheUpdater cacheUpdater;

    @KafkaListener(topics = "reservation-events", groupId = "hotel-service")
    @Transactional
    public void on(ReservationCreated event) {
        // 1. 멱등성 체크 (MySQL processed_events 테이블)
        if (processedEventRepo.existsByEventId(event.eventId())) {
            return;
        }

        // 2. 비즈니스 처리 — Redis 가용성 캐시 감소
        cacheUpdater.decreaseAvailability(
            event.hotelId(), event.roomTypeId(),
            event.checkInDate(), event.checkOutDate());

        // 3. 처리 완료 기록
        processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
```

**규칙**:
- `contracts` 에 정의된 타입만 import
- 처리 실패 시 DLQ 로 이동
- 절대 서비스 내부 Domain 타입으로 바로 받지 않음
- 멱등성 저장소는 **같은 트랜잭션의 MySQL 테이블** 사용

---

## 5. 동기 gRPC 호출

### 5.1 gRPC 서비스 제공 (서버 측)

`contracts/proto/guest.proto` 에 정의된 서비스를 `guest-service` 가 구현.

```java
// guest-service/.../infrastructure/grpc/server/GuestGrpcService.java
import com.example.contracts.proto.guest.*;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class GuestGrpcService extends GuestServiceGrpc.GuestServiceImplBase {

    private final GuestQueryService guestQueryService;

    @Override
    public void getGuest(GetGuestRequest request,
                         StreamObserver<GuestResponse> responseObserver) {
        try {
            GuestView view = guestQueryService.findById(new GuestId(request.getGuestId()));

            GuestResponse response = GuestResponse.newBuilder()
                .setGuestId(view.id().value())
                .setName(view.name())
                .setEmail(view.email())
                .setActive(view.active())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (GuestNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("Guest not found: " + request.getGuestId())
                .asRuntimeException());
        }
    }
}
```

**규칙**:
- gRPC 서비스 클래스는 **`infrastructure/grpc/server/`** 에만
- Application Service 호출. Repository 직접 호출 금지.
- Domain 뷰 → proto 응답으로 변환
- 예외 → 적절한 gRPC `Status` 매핑 (NOT_FOUND, INVALID_ARGUMENT, INTERNAL)

### 5.2 gRPC 클라이언트 호출 (호출자 측)

Application Service 가 **직접** gRPC stub 을 쓰지 않는다. Domain 에 인터페이스, Infrastructure 에서 gRPC 로 구현.

```java
// reservation-service/.../domain/service/GuestLookup.java  (Domain 인터페이스)
public interface GuestLookup {
    Optional<GuestSummary> findById(GuestId id);
}

// reservation-service/.../domain/model/guest/GuestSummary.java
public record GuestSummary(GuestId id, String name, boolean active) {}
```

```java
// reservation-service/.../infrastructure/grpc/client/GrpcGuestLookup.java
import com.example.contracts.proto.guest.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;

@Component
public class GrpcGuestLookup implements GuestLookup {

    @GrpcClient("guest-service")
    private GuestServiceGrpc.GuestServiceBlockingStub stub;

    @Override
    @CircuitBreaker(name = "guest-service", fallbackMethod = "fallback")
    @Retry(name = "guest-service")
    public Optional<GuestSummary> findById(GuestId id) {
        try {
            GuestResponse response = stub
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .getGuest(GetGuestRequest.newBuilder()
                    .setGuestId(id.value())
                    .build());

            return Optional.of(new GuestSummary(
                new GuestId(response.getGuestId()),
                response.getName(),
                response.getActive()
            ));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw new GuestServiceException(id, e);
        }
    }

    private Optional<GuestSummary> fallback(GuestId id, Throwable t) {
        throw new GuestServiceUnavailableException(id, t);
    }
}
```

**규칙**:
- `@GrpcClient` 주입은 **`infrastructure/grpc/client/`** 에만
- **Deadline 필수** (`withDeadlineAfter`)
- Resilience4j `@CircuitBreaker` + `@Retry` 필수
- contracts proto 응답 → Domain 객체 변환
- `StatusRuntimeException` 을 도메인 예외로 변환

### 5.3 gRPC 설정 예시

```yaml
# reservation-service/src/main/resources/application.yml
grpc:
  client:
    guest-service:
      address: "static://${services.guest.host:guest-service}:${services.guest.grpc-port:9090}"
      negotiation-type: plaintext
      enable-keep-alive: true
      keep-alive-time: 30s
      keep-alive-timeout: 5s

resilience4j:
  circuitbreaker:
    instances:
      guest-service:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
  retry:
    instances:
      guest-service:
        max-attempts: 3
        wait-duration: 500ms
```

---

## 6. 데이터 일관성 — Saga 패턴

여러 서비스에 걸친 트랜잭션은 분산 트랜잭션 대신 Saga 로 처리.

예: 예약 생성
1. `reservation-service`: 투숙객 유효성 확인 (동기 gRPC)
2. `reservation-service`: `RoomTypeInventory` 차감 + `Reservation` 생성 (로컬 MySQL 트랜잭션)
3. 이벤트 발행: `ReservationCreated` (Outbox)
4. `hotel-service`: 가용성 Redis 캐시 갱신 (구독)
5. `rate-service`: 요금 정산 레코드 생성 (구독)
6. `guest-service`: 방문 이력 업데이트 (구독)

**보상 트랜잭션 예**: 5번 실패 → `BillingCreationFailed` → reservation-service 가 예약 취소 (재고 복원).

상세 → `references/saga-example.md`

---

## 7. Read Model 동기화 (hotel-service 가용성 캐시)

### 7.1 구조

```
[reservation-service] (SoT)
  RoomTypeInventory (MySQL, Aggregate)
  - 예약 생성: inventory.decrease()
  - 예약 취소: inventory.increase()
  - ReservationCreated / ReservationCancelled 이벤트 발행
         ↓ Kafka
[hotel-service] (Read Model)
  RoomAvailabilityView (Redis)
  - 이벤트 구독해서 수치 갱신
  - 조회 API 가 Redis 에서 읽음
```

### 7.2 핵심 규칙

- **이름을 다르게 한다**: 쓰기는 `RoomTypeInventory`, 읽기는 `RoomAvailabilityView`. 같은 이름 금지.
- **Read Model 은 Aggregate 가 아니다**: JPA Entity 아님, 불변식 검증 없음, 단순 DTO/Redis key-value.
- **stale 허용**: 조회 응답에 "최대 수 초 지연" 명시 가능. 예약 확정은 reservation-service 에서 재검증.
- **캐시 재구축**: reservation-service 의 전체 `RoomTypeInventory` 조회 후 Redis 재구축 배치 필수 (이벤트 유실 대비).
- **TTL 과 갱신 정책 명시**: 예) 24시간 TTL + 이벤트 기반 갱신.

상세 구현 → `references/read-model-sync.md`

---

## 8. 모듈 의존성 검증

### 8.1 `build.gradle.kts` 규칙

```kotlin
// reservation-service/build.gradle.kts
dependencies {
    implementation(project(":contracts"))                // ✅
    implementation(project(":common-infrastructure"))    // ✅

    // implementation(project(":hotel-service"))         // ❌
    // implementation(project(":guest-service"))         // ❌

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // MySQL
    runtimeOnly("com.mysql:mysql-connector-j")

    // gRPC
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
}
```

### 8.2 ArchUnit 자동 검증

상세 → `references/archunit-rules.md`

---

## 9. 자가 검증 체크리스트

### 이벤트 발행
- [ ] `contracts/event/` 에 스키마 정의?
- [ ] `eventId`, `occurredAt` 필드?
- [ ] 발행자/구독자/토픽/용도 JavaDoc?
- [ ] Outbox 패턴 사용?

### 이벤트 구독
- [ ] 멱등성 처리(`ProcessedEvent`)?
- [ ] contracts 타입만 받고 Domain 변환?
- [ ] 실패 시 DLQ 이동?

### gRPC 호출
- [ ] Domain 인터페이스로 래핑?
- [ ] `withDeadlineAfter(...)` 설정?
- [ ] Circuit Breaker + Retry?
- [ ] Fallback 정의?
- [ ] proto → Domain 변환?
- [ ] `StatusRuntimeException` → 도메인 예외 매핑?

### gRPC 서비스 제공
- [ ] `@GrpcService` 가 `infrastructure/grpc/server/` 에?
- [ ] Application Service 경유?
- [ ] Domain 예외 → gRPC `Status` 매핑?

### Read Model
- [ ] 쓰기/읽기 이름 구분?
- [ ] 캐시 재구축 배치?
- [ ] stale 정책 명시?
- [ ] SoT 재검증?

### Saga
- [ ] 각 단계 보상 트랜잭션?
- [ ] 실패 시나리오 최종 상태?
- [ ] ADR 문서화?

### 의존성
- [ ] 다른 서비스 모듈 참조 없음?
- [ ] ArchUnit 통과?

---

## 10. 안티패턴 (즉시 차단)

| 안티패턴 | 올바른 방법 |
|---|---|
| 다른 서비스 MySQL 에 직접 쿼리 | gRPC 호출 또는 이벤트 구독으로 복제 |
| `contracts` 에 Aggregate / JpaEntity 둠 | 서비스 내부 유지. contracts 는 proto/이벤트만 |
| gRPC stub 을 Application Service 에서 직접 호출 | Domain 인터페이스로 래핑 |
| gRPC 호출에 Deadline 미설정 | **반드시** `withDeadlineAfter(...)` |
| Kafka Consumer 에 멱등성 없음 | `ProcessedEvent` 테이블 |
| 서비스 모듈간 `implementation(project(...))` 참조 | 금지. contracts / common-infrastructure 만 |
| proto 필드 번호 재사용 / 의미 변경 | `reserved` 로 영구 차단 |
| 쓰기 Aggregate 와 Read Model 이름 동일 | 명확히 구분 (`Inventory` vs `AvailabilityView`) |
| Redis 캐시만 믿고 예약 확정 | 반드시 SoT 에서 재검증 |
| 분산 트랜잭션 (2PC, JTA) 시도 | Saga + 보상 트랜잭션 |

---

## 다음 단계

→ `.claude/skills/ddd-architecture/SKILL.md`

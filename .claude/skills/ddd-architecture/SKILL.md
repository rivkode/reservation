---
name: ddd-architecture
description: Java/Spring 프로젝트에서 DDD 원칙을 적용해 코드를 작성/수정할 때 사용한다. 도메인 객체와 JPA Entity 분리, Aggregate/Entity/Value Object 설계, Repository 인터페이스와 구현 분리, 계층간 의존성 방향, 매퍼 패턴, 도메인 이벤트를 다룬다. "도메인 모델링", "엔티티 추가", "서비스 구현", "Repository 작성", "Aggregate", "Value Object" 키워드가 나오거나 code-planning 단계가 완료된 직후 반드시 사용. Controller/Service/Entity 단어만 나와도 이 스킬로 설계 원칙을 먼저 확인한다.
---

# DDD Architecture

계획 단계(`code-planning`) 이후 실제 구현 시 참조한다.
**핵심 원칙**: 도메인은 프레임워크로부터 자유로워야 한다.

이 스킬은 **단일 서비스 모듈 내부**의 계층 설계를 다룬다.
서비스간 통신, 공유 계약은 `.claude/skills/module-boundary/SKILL.md` 에서 다룬다.

---

## 0. MSA 멀티모듈 맥락

각 서비스(`hotel-service`, `rate-service`, `guest-service`, `reservation-service`)는 **독립적인 Bounded Context** 이며 아래 규칙은 **각 서비스 모듈 내부**에 적용된다.

- 각 서비스는 자체 `domain/`, `application/`, `infrastructure/`, `presentation/` 패키지를 가진다
- **다른 서비스의 Domain / JpaEntity 를 직접 import 하지 않는다** (module-boundary 원칙)
- 외부 서비스 데이터가 필요하면 Domain 에 인터페이스를 두고, Infrastructure 에서 gRPC / Kafka 로 구현

---

## 1. 계층과 의존성

```
Presentation  (Controller, Request/Response DTO)
    ↓
Application   (Application Service, Command/Query)
    ↓
Domain        (Aggregate, Entity, VO, Domain Service, Event, Repository 인터페이스)
    ↑
Infrastructure (JpaEntity, Repository 구현, Mapper, 외부 시스템 연동)
```

- Domain은 **어떤 계층에도 의존하지 않는다**.
- Application은 Domain에만 의존한다.
- Infrastructure는 Domain의 인터페이스를 구현한다 (의존성 역전).
- Presentation은 Application에 의존한다.

**금지**:
- ❌ Controller → Repository 직접 호출
- ❌ Domain → JPA / Spring / Jackson
- ❌ Application → Presentation DTO
- ❌ JpaEntity ↔ Domain 객체 혼용

---

## 2. Domain 계층 규칙

### 2.1 Aggregate / Entity

- **JPA/Spring/Jackson 어노테이션 절대 금지**: `@Entity`, `@Component`, `@JsonProperty` 등
- **public setter 금지** — 상태 변경은 의미 있는 메서드(`cancel()`, `approve()`)로
- 생성은 **static 팩토리 메서드**(`place`, `create`)
- 불변식은 생성자/메서드에서 **즉시 검증**
- ID는 **Value Object로 래핑** (`OrderId`, `CustomerId`) — 원시 `Long`/`String` 금지
- Collection 반환 시 **방어적 복사** 또는 `Collections.unmodifiableList`
- `equals`/`hashCode`: Aggregate Root는 ID 기반, VO는 값 기반

### 2.2 Value Object

- `record` 또는 불변 클래스
- 생성 시점에 유효성 검증
- 여러 필드가 항상 함께 다니면 VO로 묶기 (`Address(street, city, zipCode)`)

### 2.3 Repository

- **인터페이스만 Domain에** 둔다. 구현체는 Infrastructure.
- 반환 타입은 **Domain 객체**. JpaEntity 노출 금지.

### 2.4 Domain Service

- 하나의 Aggregate로 끝나는 로직은 **Aggregate 내부**에.
- 여러 Aggregate에 걸친 로직만 Domain Service로 분리.

### 2.5 Domain Event

- 이름은 **과거형**: `OrderCancelled` ✅ / `CancelOrder` ❌
- Aggregate 내부에서 수집 → Application Service에서 `save()` 후 발행
- 데이터는 구독자가 필요한 최소한만

상세 코드 예시 → `references/examples-domain.md`

---

## 3. Application 계층 규칙

- `@Service`, `@Transactional` 은 **여기에만** 붙는다
- 트랜잭션 경계 = Application Service 메서드 단위
- **Command / Query 객체**로 입력받음 (원시 타입 나열 금지)
- 시간은 `Clock` 주입 → `Instant.now(clock)` (테스트 가능성)
- Application Service는 **얇게**: 도메인 조립 + Repository 호출. 로직은 도메인에 위임
- 조회 전용은 `@Transactional(readOnly = true)`
- **다른 Application Service를 직접 호출하지 않는다** (Domain Service 또는 이벤트로 해결)
- 외부 시스템 호출이 필요하면 **Domain에 인터페이스**를 두고 Infrastructure가 구현

상세 코드 예시 → `references/examples-application.md`

---

## 4. Infrastructure 계층 규칙

### 4.1 JPA Entity

- 클래스명은 **`XxxJpaEntity`** 로 끝낸다 (Domain과 이름 충돌 방지)
- **비즈니스 메서드 금지** — DB 매핑 전용
- `protected` no-arg 생성자 (JPA 요구)
- DB 구조에 최적화된 형태로 설계 가능 (Domain과 다를 수 있음)

### 4.2 Mapper

- `Domain ↔ JpaEntity` **양방향** 변환 제공
- DB에서 복원할 때는 `Xxx.reconstitute(...)` 팩토리 메서드 사용 (불변식 검증 스킵 용도)

### 4.3 Repository 구현

- Domain의 Repository 인터페이스를 구현
- 내부적으로 Spring Data JPA 인터페이스 사용, **외부로 노출하지 않음**

### 4.4 외부 시스템 연동

- Domain 에 정의한 인터페이스를 Infrastructure 에서 구현
- 네트워크 오류, 재시도, 서킷브레이커는 **이 계층에서** 처리
- **다른 MSA 서비스 호출도 동일 패턴**: Domain 에 `GuestLookup` 같은 인터페이스 → Infrastructure 의 `GrpcGuestLookup` 가 gRPC stub 으로 구현 (Deadline + Circuit Breaker 필수, 상세는 `module-boundary` 스킬 참조)

상세 코드 예시 → `references/examples-infrastructure.md`

---

## 5. Presentation 계층 규칙

- Controller는 **얇게**: 입력 검증, DTO 변환, Application 호출만
- **Request/Response DTO와 Application Command를 분리**
- 입력 검증(`@Valid`, `@NotBlank`)은 **Presentation DTO에만**
- Domain 객체를 Response로 **직접 반환 금지**
- 예외는 `@RestControllerAdvice` 에서 공통 처리

상세 코드 예시 → `references/examples-presentation.md`

---

## 6. 자가 검증 체크리스트

구현 후 각 계층별로 점검한다.

### Domain
- [ ] JPA/Spring 어노테이션이 없는가?
- [ ] public setter가 없는가?
- [ ] 생성자에서 불변식을 검증하는가?
- [ ] ID가 VO로 래핑되어 있는가?
- [ ] 상태 변경 메서드가 의미 있는 이름인가?
- [ ] import가 `java.*` 와 프로젝트 내부 패키지뿐인가?

### Application
- [ ] `@Transactional` 경계가 명확한가?
- [ ] Command/Query 객체로 입력받는가?
- [ ] `Clock` 주입으로 시간을 처리하는가?
- [ ] Domain 객체를 Presentation에 그대로 노출하지 않는가?

### Infrastructure
- [ ] JpaEntity가 `XxxJpaEntity` 네이밍인가?
- [ ] Mapper가 양방향 변환을 제공하는가?
- [ ] Spring Data JPA 인터페이스가 Domain에 노출되지 않는가?

### Presentation
- [ ] Controller가 Repository를 직접 주입받지 않는가?
- [ ] Request DTO에 입력 검증 어노테이션이 있는가?
- [ ] Domain 객체를 Response로 직접 반환하지 않는가?

---

## 7. 설계 판단 가이드

**VO vs Entity?** — 정체성이 중요하면 Entity, 값 자체가 의미면 VO. 애매하면 VO로 시작.

**Aggregate 경계?** — 외부에서 참조 가능한 단위가 Aggregate Root. 한 트랜잭션 = 한 AR 수정.

**로직 배치?**
- 하나의 Aggregate 안에서 완결 → Aggregate 메서드
- 여러 Aggregate 걸침 → Domain Service
- 트랜잭션/외부 호출 필요 → Application Service

**이벤트 발행?** — Aggregate 내부에서 수집 → Application Service에서 `save()` 후 발행 → `@TransactionalEventListener(AFTER_COMMIT)` 로 후처리.

주요 안티패턴 7가지 → `references/anti-patterns.md`

---

## 다음 단계

구현이 끝나면 → `.claude/skills/testing-junit/SKILL.md`

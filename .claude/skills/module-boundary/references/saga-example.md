# Saga 패턴 예시 — 예약 생성 플로우

여러 서비스에 걸친 트랜잭션을 분산 트랜잭션 없이 최종 일관성으로 처리.

**스택**: gRPC (동기 호출), Kafka (비동기 이벤트), MySQL (로컬 트랜잭션 + Outbox), Redis (읽기 캐시)

---

## 플로우

```
[client] → POST /reservations
             ↓
[reservation-service]
   1. 투숙객 유효성 확인 (동기 — guest-service gRPC 호출, Deadline 3s)
   2. RoomTypeInventory 차감 + Reservation 생성 (로컬 MySQL 트랜잭션)
   3. Outbox 테이블에 ReservationCreated 적재 (같은 트랜잭션)
             ↓
        Outbox Relay (스케줄러) → Kafka (reservation-events 토픽)
        ↙              ↓                 ↘
[hotel-service]  [rate-service]    [guest-service]
  Redis 가용성      BillingCreated      VisitHistoryUpdated
  캐시 감소         or
                  BillingCreationFailed
             ↓
[reservation-service]  구독
  - 실패 시 Reservation 취소 (보상 트랜잭션 — 재고 복원)
```

---

## 코드

### 1. 예약 생성 Application Service

```java
@Service
public class CreateReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomTypeInventoryRepository inventoryRepository;
    private final GuestLookup guestLookup;           // Domain 인터페이스 (gRPC)
    private final OutboxEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public ReservationId create(CreateReservationCommand command) {
        Instant now = Instant.now(clock);

        // 1. 동기 조회 — 투숙객 존재 확인 (gRPC)
        guestLookup.findById(command.guestId())
            .orElseThrow(() -> new GuestNotFoundException(command.guestId()));

        // 2. 로컬 트랜잭션 — 재고 차감 + 예약 생성
        RoomTypeInventory inventory = inventoryRepository
            .findByHotelAndRoomTypeAndDate(
                command.hotelId(), command.roomTypeId(), command.checkInDate())
            .orElseThrow(() -> new InventoryNotFoundException(
                command.hotelId(), command.roomTypeId(), command.checkInDate()));

        inventory.decrease(command.numberOfRooms());
        inventoryRepository.save(inventory);

        Reservation reservation = Reservation.create(
            ReservationId.generate(),
            command.guestId(), command.hotelId(), command.roomTypeId(),
            command.checkInDate(), command.checkOutDate(),
            command.numberOfGuests(), now);
        reservationRepository.save(reservation);

        // 3. Outbox 에 이벤트 적재 (같은 MySQL 트랜잭션)
        eventPublisher.publish(new ReservationCreated(
            UUID.randomUUID().toString(),
            now,
            reservation.id().value(),
            command.guestId().value(),
            command.hotelId().value(),
            command.roomTypeId().value(),
            command.checkInDate(),
            command.checkOutDate(),
            command.numberOfGuests()
        ), "reservation-events");

        return reservation.id();
    }
}
```

**핵심**: 1번 gRPC 호출은 트랜잭션 **전**. 2~3번이 한 트랜잭션. 외부 호출과 DB 쓰기를 트랜잭션 안에 섞지 않는다.

### 2. 가용성 캐시 갱신 리스너 (hotel-service)

```java
@Component
public class ReservationCreatedAvailabilityListener {

    private final ProcessedEventRepository processedEventRepo;
    private final RoomAvailabilityCacheUpdater cacheUpdater;   // Redis 접근

    @KafkaListener(topics = "reservation-events", groupId = "hotel-service")
    @Transactional
    public void on(ReservationCreated event) {
        if (processedEventRepo.existsByEventId(event.eventId())) {
            return;
        }

        // Redis 캐시에서 해당 호텔/타입/날짜 범위의 가용성 감소
        cacheUpdater.decreaseAvailability(
            event.hotelId(), event.roomTypeId(),
            event.checkInDate(), event.checkOutDate());

        processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
```

**주의**: Redis 실패 시에도 MySQL 트랜잭션은 커밋되어야 함 → 캐시 불일치 발생. 정기적인 **캐시 재구축 배치**로 복구.

### 3. 요금 정산 리스너 (rate-service)

```java
@Component
public class ReservationCreatedBillingListener {

    private final ProcessedEventRepository processedEventRepo;
    private final BillingService billingService;
    private final OutboxEventPublisher eventPublisher;

    @KafkaListener(topics = "reservation-events", groupId = "rate-service")
    @Transactional
    public void on(ReservationCreated event) {
        if (processedEventRepo.existsByEventId(event.eventId())) {
            return;
        }

        try {
            billingService.createBilling(new CreateBillingCommand(
                event.reservationId(), event.roomTypeId(),
                event.checkInDate(), event.checkOutDate()));

            processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now()));

        } catch (RoomTypeRateNotFoundException e) {
            // 요금 정보 없음 → 예약 서비스에 실패 알림 (보상 트리거)
            eventPublisher.publish(new BillingCreationFailed(
                UUID.randomUUID().toString(),
                Instant.now(),
                event.reservationId(),
                e.getMessage()
            ), "billing-events");

            processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now()));
        }
    }
}
```

### 4. 보상 트랜잭션 리스너 (reservation-service)

```java
@Component
public class BillingCreationFailedListener {

    private final ProcessedEventRepository processedEventRepo;
    private final CancelReservationService cancelReservationService;

    @KafkaListener(topics = "billing-events", groupId = "reservation-service")
    public void on(BillingCreationFailed event) {
        if (processedEventRepo.existsByEventId(event.eventId())) {
            return;
        }

        // 예약 취소 (보상 트랜잭션) — 내부에서 재고 복원 + ReservationCancelled 이벤트 발행
        cancelReservationService.cancel(new CancelReservationCommand(
            new ReservationId(event.reservationId()),
            new CancellationReason("BILLING_FAILED")
        ));

        processedEventRepo.save(new ProcessedEvent(event.eventId(), Instant.now()));
    }
}
```

---

## 핵심 체크리스트

- [ ] 각 단계가 **로컬 트랜잭션**으로 완결?
- [ ] 이벤트 발행이 **Outbox** 로 DB 와 원자성 보장?
- [ ] 외부 gRPC 호출이 **트랜잭션 밖**에?
- [ ] gRPC 에 **Deadline** 설정?
- [ ] 구독 측 **멱등성** 보장?
- [ ] **보상 트랜잭션** 정의?
- [ ] 최종 상태 (성공 / 보상 / 데드레터) 명확?
- [ ] 캐시 재구축 배치 존재?
- [ ] ADR 문서화?

# Read Model 동기화 — hotel-service 가용성 캐시

**SoT (쓰기)**: reservation-service 의 `RoomTypeInventory` (MySQL)
**Read Model (읽기)**: hotel-service 의 `RoomAvailabilityView` (Redis)

---

## 1. 왜 분리하는가

### 쓰기 쪽 요구사항
- 오버부킹 방지 → 같은 트랜잭션 안에서 재고 차감
- **정확성** 최우선

### 읽기 쪽 요구사항
- 사용자가 날짜/객실 타입으로 가용성을 빠르게 조회
- **응답 속도** 최우선, 약간의 stale 허용

→ CQRS 로 분리. 서로 다른 저장소(MySQL vs Redis)와 다른 모델.

---

## 2. 자료구조

### SoT (MySQL)

```sql
CREATE TABLE room_type_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_id       VARCHAR(36) NOT NULL,
    room_type_id   VARCHAR(36) NOT NULL,
    date           DATE NOT NULL,
    total_rooms    INT NOT NULL,
    available_rooms INT NOT NULL,
    version        BIGINT NOT NULL,                    -- 낙관적 락
    UNIQUE KEY uk_inv_hotel_type_date (hotel_id, room_type_id, date)
) ENGINE=InnoDB;
```

### Read Model (Redis)

```
KEY:   availability:{hotelId}:{roomTypeId}:{YYYY-MM-DD}
VALUE: { "available": 3, "total": 10, "updatedAt": "2026-04-21T10:00:00Z" }
TTL:   48시간 (정기 배치로 재구축)
```

---

## 3. 동기화 흐름

### 3.1 예약 생성 시

```
reservation-service
  - Inventory.decrease() + Reservation 생성 (MySQL 트랜잭션)
  - Outbox 에 ReservationCreated 적재
          ↓
     Kafka reservation-events
          ↓
hotel-service Listener
  - Redis availability:{...} 의 available 값 감소
```

### 3.2 예약 취소 시

```
reservation-service
  - Inventory.increase() + Reservation 상태 변경 (MySQL 트랜잭션)
  - Outbox 에 ReservationCancelled 적재
          ↓
     Kafka reservation-events
          ↓
hotel-service Listener
  - Redis availability:{...} 의 available 값 증가
```

### 3.3 hotel-service 에서 방 추가 시

```
hotel-service
  - Room 생성 (MySQL)
  - Outbox 에 RoomCreated 적재
          ↓
     Kafka hotel-events
          ↓
reservation-service Listener (SoT 갱신)
  - RoomTypeInventory 레코드 생성 (향후 날짜별)
          ↓ (다시 이벤트로 알리거나 배치로)
hotel-service Redis 캐시 초기화
```

---

## 4. 코드

### 4.1 Redis 접근 (Infrastructure)

```java
// hotel-service/.../infrastructure/cache/RoomAvailabilityCacheUpdater.java
@Component
public class RoomAvailabilityCacheUpdater {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration TTL = Duration.ofHours(48);

    public void decreaseAvailability(String hotelId, String roomTypeId,
                                      LocalDate checkIn, LocalDate checkOut) {
        LocalDate date = checkIn;
        while (date.isBefore(checkOut)) {
            String key = buildKey(hotelId, roomTypeId, date);
            updateAvailability(key, -1);
            date = date.plusDays(1);
        }
    }

    public void increaseAvailability(String hotelId, String roomTypeId,
                                     LocalDate checkIn, LocalDate checkOut) {
        LocalDate date = checkIn;
        while (date.isBefore(checkOut)) {
            String key = buildKey(hotelId, roomTypeId, date);
            updateAvailability(key, +1);
            date = date.plusDays(1);
        }
    }

    private void updateAvailability(String key, int delta) {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                // 캐시 미존재 → 배치로 재구축될 때까지 skip
                return null;
            }
            try {
                AvailabilityEntry entry = objectMapper.readValue(json, AvailabilityEntry.class);
                int newAvailable = Math.max(0, entry.available() + delta);
                AvailabilityEntry updated = new AvailabilityEntry(
                    newAvailable, entry.total(), Instant.now());
                redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(updated), TTL);
            } catch (JsonProcessingException e) {
                throw new CacheUpdateException(key, e);
            }
            return null;
        });
    }

    private String buildKey(String hotelId, String roomTypeId, LocalDate date) {
        return String.format("availability:%s:%s:%s", hotelId, roomTypeId, date);
    }

    public record AvailabilityEntry(int available, int total, Instant updatedAt) {}
}
```

### 4.2 조회 API (Presentation / Application)

```java
// hotel-service/.../application/service/AvailabilityQueryService.java
@Service
public class AvailabilityQueryService {

    private final RoomAvailabilityCacheReader cacheReader;

    public List<RoomAvailabilityView> findAvailability(
            String hotelId, String roomTypeId, LocalDate from, LocalDate to) {
        // Redis 에서 읽기 (빠름)
        // 캐시 미스 시 reservation-service gRPC 호출 or 빈 결과
        return cacheReader.read(hotelId, roomTypeId, from, to);
    }
}
```

**중요**: 이 응답은 **stale 할 수 있다**. 예약 확정 시 reservation-service 가 다시 검증.

---

## 5. 캐시 재구축 배치

이벤트 유실, Redis 장애 복구 대비.

```java
// hotel-service/.../infrastructure/cache/AvailabilityCacheRebuildJob.java
@Component
public class AvailabilityCacheRebuildJob {

    private final InventoryLookup inventoryLookup;    // Domain 인터페이스 (reservation-service gRPC)
    private final RoomAvailabilityCacheUpdater cacheUpdater;

    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
    public void rebuild() {
        // reservation-service 에 gRPC 로 전체 Inventory 스트리밍 요청
        inventoryLookup.streamAll(inventory -> {
            cacheUpdater.put(
                inventory.hotelId(),
                inventory.roomTypeId(),
                inventory.date(),
                inventory.available(),
                inventory.total());
        });
    }
}
```

`InventoryLookup` 은 `contracts/proto/reservation.proto` 의 server-streaming RPC 를 호출하는 gRPC 클라이언트 구현.

---

## 6. 규칙 체크리스트

- [ ] 쓰기 Aggregate (`RoomTypeInventory`) 와 읽기 모델 (`RoomAvailabilityView`) 이름이 다른가?
- [ ] 읽기 모델은 **JPA Entity 가 아닌가**? (Redis key-value 또는 단순 DTO)
- [ ] 읽기 모델에 불변식 검증이 없는가? (단순 조회용)
- [ ] 캐시 미스 / 장애 시 **재구축 경로**가 있는가? (배치, 또는 gRPC 폴백)
- [ ] 조회 응답에 stale 가능성이 문서화되어 있는가?
- [ ] 예약 확정은 **SoT 서비스 (reservation-service)** 에서 재검증하는가?
- [ ] TTL 과 갱신 정책이 명시되어 있는가?

---

## 7. 안티패턴

| 안티패턴 | 올바른 방법 |
|---|---|
| `RoomTypeInventory` 를 hotel-service 에도 둠 (이름 충돌) | hotel-service 는 `RoomAvailabilityView` 로 명명 |
| Redis 캐시를 Aggregate 로 취급 (@Entity, 불변식) | 단순 key-value / DTO. 로직 없음. |
| 캐시만 읽고 예약 확정 | reservation-service 에서 재검증 필수 |
| 캐시 재구축 배치 없음 | 이벤트 유실 복구 불가. 반드시 배치 / 수동 트리거 제공 |
| 캐시 실패 시 예약 트랜잭션 롤백 | 캐시 실패는 예약과 독립. 로그 + 재구축으로 복구. |

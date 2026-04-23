package com.reservation.reservation.domain.model;

import com.reservation.reservation.domain.exception.InvalidInventoryOperationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * reservation-service 의 재고 SoT (Source of Truth) Aggregate Root.
 *
 * <p>자연 복합 키 {@link InventoryKey} {@code (hotelId, roomTypeId, stayDate)} 기준으로
 * 하루 단위 집계 row 를 표현한다. 각 row 는 "해당 날짜에 존재하는 해당 타입 객실의 총수"
 * 와 "현재 예약 가능 객실 수" 를 유지한다.
 *
 * <p>PR-2.1 범위에서는 hotel-events 구독으로 {@link #addRoom(Clock)} · {@link #removeRoom(Clock)}
 * 만 호출된다. {@code decrease}/{@code restore} (예약 차감/취소 복원) 는 PR-2.2/2.3 에서
 * 추가된다. {@link #version} 은 본 PR 에서 주입만 받아두고 실 OCC 경합은 PR-2.2 예약 경로에서
 * 검증한다 — 지금 도입하는 이유는 JPA Entity 스키마를 미리 고정해 후속 PR 의 마이그레이션
 * 부담을 없애기 위함이다.
 *
 * <p>불변식:
 * <ul>
 *   <li>{@code totalRooms ≥ 0}, {@code availableRooms ≥ 0} — {@link InventoryCount} 가 VO 수준에서 보장</li>
 *   <li>{@code availableRooms ≤ totalRooms} — 본 Aggregate 가 연산 후 검증</li>
 *   <li>{@code createdAt ≤ updatedAt}</li>
 * </ul>
 *
 * <p>Tombstone 정책: {@code removeRoom} 으로 총량이 0 이 되어도 row 는 유지한다 (감사 ·
 * 재전송 내성). 삭제는 Repository 도 수행하지 않는다.
 */
public class RoomTypeInventory {

    private final InventoryKey key;
    private InventoryCount totalRooms;
    private InventoryCount availableRooms;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoomTypeInventory(InventoryKey key,
                              InventoryCount totalRooms,
                              InventoryCount availableRooms,
                              long version,
                              Instant createdAt,
                              Instant updatedAt) {
        this.key = Objects.requireNonNull(key, "key");
        this.totalRooms = Objects.requireNonNull(totalRooms, "totalRooms");
        this.availableRooms = Objects.requireNonNull(availableRooms, "availableRooms");
        if (availableRooms.value() > totalRooms.value()) {
            throw new IllegalArgumentException(
                "availableRooms (" + availableRooms.value() + ") must not exceed totalRooms ("
                    + totalRooms.value() + ")");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * 신규 Inventory row 생성. {@code totalRooms}/{@code availableRooms} 모두 0 으로 시작하며,
     * Application Service 가 이어서 {@link #addRoom(Clock)} 을 호출해 1로 올린다. 이 분리는
     * "존재하지 않던 날짜에 객실이 추가됨" 이라는 의미를 create + addRoom 2 단계로 드러내
     * 향후 동일 로직이 RoomCreated 외 경로(예: horizon 확장 배치)로도 쓰일 수 있게 한다.
     */
    public static RoomTypeInventory create(HotelId hotelId,
                                            RoomTypeId roomTypeId,
                                            LocalDate stayDate,
                                            Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new RoomTypeInventory(
            new InventoryKey(hotelId, roomTypeId, stayDate),
            InventoryCount.zero(),
            InventoryCount.zero(),
            0L,
            now,
            now
        );
    }

    /** 영속 저장소에서 복원 — Mapper 가 보유 중인 상태 그대로 주입. */
    public static RoomTypeInventory restore(InventoryKey key,
                                             InventoryCount totalRooms,
                                             InventoryCount availableRooms,
                                             long version,
                                             Instant createdAt,
                                             Instant updatedAt) {
        return new RoomTypeInventory(key, totalRooms, availableRooms, version, createdAt, updatedAt);
    }

    /**
     * 해당 RoomType 에 객실 한 개가 추가됨. total · available 모두 +1.
     *
     * <p>이벤트 중복(at-least-once) 방어는 Aggregate 가 아니라 상위 계층의
     * {@code ProcessedEventGuard} 가 담당한다 — Aggregate 는 eventId 를 알지 못한다.
     */
    public void addRoom(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        this.totalRooms = totalRooms.increment();
        this.availableRooms = availableRooms.increment();
        this.updatedAt = Instant.now(clock);
    }

    /**
     * 해당 RoomType 에서 객실 한 개가 제거됨. total · available 모두 -1.
     *
     * <p>{@code totalRooms == 0} 상태에서 호출되면 {@link InvalidInventoryOperationException}.
     * Application 계층은 이를 warn 로그로 흡수하고 processed_events 기록은 유지해 재전송을
     * 막는다.
     */
    public void removeRoom(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (totalRooms.isZero()) {
            throw new InvalidInventoryOperationException(
                "Cannot removeRoom on empty inventory " + key);
        }
        this.totalRooms = totalRooms.decrement();
        this.availableRooms = availableRooms.decrement();
        this.updatedAt = Instant.now(clock);
    }

    public InventoryKey key() {
        return key;
    }

    public HotelId hotelId() {
        return key.hotelId();
    }

    public RoomTypeId roomTypeId() {
        return key.roomTypeId();
    }

    public LocalDate stayDate() {
        return key.stayDate();
    }

    public InventoryCount totalRooms() {
        return totalRooms;
    }

    public InventoryCount availableRooms() {
        return availableRooms;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

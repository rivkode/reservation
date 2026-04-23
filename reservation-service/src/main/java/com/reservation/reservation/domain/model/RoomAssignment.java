package com.reservation.reservation.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 개별 Room 이 현재 어느 RoomType 에 속해있는지를 추적하는 보조 Aggregate Root.
 *
 * <p>{@link RoomTypeInventory} 가 "타입 단위 집계" 를 책임지므로 Room 의 타입 변경
 * ({@code RoomUpdatedEvent}) 이 일어나면 (이전 타입 Inventory -1) + (새 타입 Inventory +1)
 * 로 반영해야 한다. 그러나 contracts 의 {@code RoomUpdatedEvent} 는 이전 roomTypeId 를
 * 전달하지 않으므로, reservation-service 가 자체적으로 "현재 매핑" 을 보관해 비교할 필요가
 * 있다.
 *
 * <p>별도 Aggregate 로 분리한 이유는 {@link RoomId} 를 AR 키로 해야 `O(1)` 조회가 되고
 * (Inventory 의 복합 키로는 roomId 로 조회 불가), 생명주기가 Inventory 와 달라서다. 같은
 * 이벤트 수신 트랜잭션에서 두 AR 이 함께 바뀌지만, reservation-service 는 hotel-events
 * 의 projection 역할이므로 "1 AR per 트랜잭션" 원칙의 엄격 적용 대상이 아니다 (Vernon IDDD
 * §10 의 projection 예외).
 */
public class RoomAssignment {

    private final RoomId roomId;
    private final HotelId hotelId;
    private RoomTypeId roomTypeId;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoomAssignment(RoomId roomId,
                           HotelId hotelId,
                           RoomTypeId roomTypeId,
                           long version,
                           Instant createdAt,
                           Instant updatedAt) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.hotelId = Objects.requireNonNull(hotelId, "hotelId");
        this.roomTypeId = Objects.requireNonNull(roomTypeId, "roomTypeId");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 신규 Room 최초 매핑 등록. {@code RoomCreatedEvent} 수신 시 호출. */
    public static RoomAssignment create(RoomId roomId,
                                         HotelId hotelId,
                                         RoomTypeId roomTypeId,
                                         Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new RoomAssignment(roomId, hotelId, roomTypeId, 0L, now, now);
    }

    /** 영속 저장소에서 복원. */
    public static RoomAssignment restore(RoomId roomId,
                                          HotelId hotelId,
                                          RoomTypeId roomTypeId,
                                          long version,
                                          Instant createdAt,
                                          Instant updatedAt) {
        return new RoomAssignment(roomId, hotelId, roomTypeId, version, createdAt, updatedAt);
    }

    /**
     * 새 RoomType 으로 재할당. 실제 변경이 발생한 경우 직전 타입을 반환한다.
     *
     * @param newRoomTypeId 이벤트가 전달한 현재 roomTypeId
     * @param clock         {@code updatedAt} 갱신에 사용할 Clock
     * @return 타입이 실제로 바뀌었을 때 {@code Optional.of(이전 roomTypeId)}, 동일하면 {@code Optional.empty()}
     */
    public Optional<RoomTypeId> reassign(RoomTypeId newRoomTypeId, Clock clock) {
        Objects.requireNonNull(newRoomTypeId, "newRoomTypeId");
        Objects.requireNonNull(clock, "clock");
        if (this.roomTypeId.equals(newRoomTypeId)) {
            return Optional.empty();
        }
        RoomTypeId previous = this.roomTypeId;
        this.roomTypeId = newRoomTypeId;
        this.updatedAt = Instant.now(clock);
        return Optional.of(previous);
    }

    public RoomId roomId() {
        return roomId;
    }

    public HotelId hotelId() {
        return hotelId;
    }

    public RoomTypeId roomTypeId() {
        return roomTypeId;
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

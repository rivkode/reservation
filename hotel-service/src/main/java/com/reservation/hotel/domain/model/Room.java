package com.reservation.hotel.domain.model;

import com.reservation.hotel.domain.exception.InvalidRoomStateTransitionException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Room Aggregate Root — 호텔의 개별 물리 객실.
 *
 * <p>DB UNIQUE {@code (hotelId, floor, roomNumber)} 으로 물리적 중복 등록 방지.
 * {@link RoomStatus} 기반 soft-delete 정책을 사용해 {@code DEACTIVATED} 된 Room 도
 * 행을 보존 (예약 이력 · 감사 로그를 잃지 않기 위함). {@code DEACTIVATED} 는 종점 상태로
 * 재활성(부활) 을 금지해 의도 없는 데이터 되살리기를 차단한다.
 */
public class Room {

    private final RoomId id;
    private final HotelId hotelId;
    private RoomTypeId roomTypeId;
    private final Floor floor;
    private final RoomNumber number;
    private RoomStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Room(RoomId id,
                 HotelId hotelId,
                 RoomTypeId roomTypeId,
                 Floor floor,
                 RoomNumber number,
                 RoomStatus status,
                 long version,
                 Instant createdAt,
                 Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.hotelId = Objects.requireNonNull(hotelId, "hotelId");
        this.roomTypeId = Objects.requireNonNull(roomTypeId, "roomTypeId");
        this.floor = Objects.requireNonNull(floor, "floor");
        this.number = Objects.requireNonNull(number, "number");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Room create(HotelId hotelId,
                              RoomTypeId roomTypeId,
                              Floor floor,
                              RoomNumber number,
                              Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new Room(RoomId.newId(), hotelId, roomTypeId, floor, number,
            RoomStatus.ACTIVE, 0L, now, now);
    }

    public static Room restore(RoomId id,
                               HotelId hotelId,
                               RoomTypeId roomTypeId,
                               Floor floor,
                               RoomNumber number,
                               RoomStatus status,
                               long version,
                               Instant createdAt,
                               Instant updatedAt) {
        return new Room(id, hotelId, roomTypeId, floor, number, status, version, createdAt, updatedAt);
    }

    /** 객실의 RoomType 을 재배정 (예: 리노베이션 후 등급 변경). DEACTIVATED 상태에서는 금지. */
    public void reassignRoomType(RoomTypeId newRoomTypeId, Clock clock) {
        Objects.requireNonNull(newRoomTypeId, "newRoomTypeId");
        ensureNotDeactivated("reassignRoomType");
        this.roomTypeId = newRoomTypeId;
        this.updatedAt = Instant.now(clock);
    }

    /** 점검 시작. ACTIVE 상태에서만 허용. */
    public void startMaintenance(Clock clock) {
        if (this.status != RoomStatus.ACTIVE) {
            throw new InvalidRoomStateTransitionException(id, status, "startMaintenance");
        }
        this.status = RoomStatus.UNDER_MAINTENANCE;
        this.updatedAt = Instant.now(clock);
    }

    /** 점검 완료. UNDER_MAINTENANCE 상태에서만 허용. */
    public void completeMaintenance(Clock clock) {
        if (this.status != RoomStatus.UNDER_MAINTENANCE) {
            throw new InvalidRoomStateTransitionException(id, status, "completeMaintenance");
        }
        this.status = RoomStatus.ACTIVE;
        this.updatedAt = Instant.now(clock);
    }

    /**
     * 객실 폐지 (soft-delete). 종점 상태이며 이후 상태 전이를 허용하지 않는다.
     * 이미 DEACTIVATED 인 Room 에 다시 호출하면 idempotent 하게 무영향.
     */
    public void deactivate(Clock clock) {
        if (this.status == RoomStatus.DEACTIVATED) {
            return;
        }
        this.status = RoomStatus.DEACTIVATED;
        this.updatedAt = Instant.now(clock);
    }

    private void ensureNotDeactivated(String operation) {
        if (this.status == RoomStatus.DEACTIVATED) {
            throw new InvalidRoomStateTransitionException(id, status, operation);
        }
    }

    public RoomId id() {
        return id;
    }

    public HotelId hotelId() {
        return hotelId;
    }

    public RoomTypeId roomTypeId() {
        return roomTypeId;
    }

    public Floor floor() {
        return floor;
    }

    public RoomNumber number() {
        return number;
    }

    public RoomStatus status() {
        return status;
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

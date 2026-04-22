package com.reservation.hotel.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * RoomType Aggregate Root — 특정 Hotel 에 속하는 객실 타입(예: Standard Double).
 *
 * <p>Hotel 과는 hotelId 참조로만 연결되어 두 Aggregate 를 한 트랜잭션에서 동시에
 * 수정하지 않는다. 이름 중복(`hotelId`, `name`) 은 UNIQUE 제약 + Application Service
 * 의 선제 조회로 이중 방어.
 */
public class RoomType {

    private final RoomTypeId id;
    private final HotelId hotelId;
    private RoomTypeName name;
    private MaxOccupancy maxOccupancy;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoomType(RoomTypeId id,
                     HotelId hotelId,
                     RoomTypeName name,
                     MaxOccupancy maxOccupancy,
                     long version,
                     Instant createdAt,
                     Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.hotelId = Objects.requireNonNull(hotelId, "hotelId");
        this.name = Objects.requireNonNull(name, "name");
        this.maxOccupancy = Objects.requireNonNull(maxOccupancy, "maxOccupancy");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static RoomType create(HotelId hotelId,
                                  RoomTypeName name,
                                  MaxOccupancy maxOccupancy,
                                  Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new RoomType(RoomTypeId.newId(), hotelId, name, maxOccupancy, 0L, now, now);
    }

    public static RoomType restore(RoomTypeId id,
                                   HotelId hotelId,
                                   RoomTypeName name,
                                   MaxOccupancy maxOccupancy,
                                   long version,
                                   Instant createdAt,
                                   Instant updatedAt) {
        return new RoomType(id, hotelId, name, maxOccupancy, version, createdAt, updatedAt);
    }

    public void rename(RoomTypeName newName, Clock clock) {
        Objects.requireNonNull(newName, "newName");
        this.name = newName;
        this.updatedAt = Instant.now(clock);
    }

    public void changeMaxOccupancy(MaxOccupancy newOccupancy, Clock clock) {
        Objects.requireNonNull(newOccupancy, "newOccupancy");
        this.maxOccupancy = newOccupancy;
        this.updatedAt = Instant.now(clock);
    }

    public RoomTypeId id() {
        return id;
    }

    public HotelId hotelId() {
        return hotelId;
    }

    public RoomTypeName name() {
        return name;
    }

    public MaxOccupancy maxOccupancy() {
        return maxOccupancy;
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

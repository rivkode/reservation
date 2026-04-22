package com.reservation.hotel.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Hotel Aggregate Root — 호텔 마스터 정보와 편의시설 집합을 보유한다.
 *
 * <p>RoomType · Room 은 별도 Aggregate 로, 본 Aggregate 는 hotelId 로만 교차 참조한다
 * (Eric Evans §6 "Large Aggregates" 회피). 따라서 호텔 한 건의 수정이 객실 수백 건을
 * 같은 트랜잭션에 끌어오지 않는다.
 *
 * <p>불변식:
 * <ul>
 *   <li>name / address / starRating 은 null 아님 — VO 생성자에서 강제</li>
 *   <li>amenities 는 중복 없는 Set — Enum 이므로 EnumSet 으로 보관</li>
 *   <li>createdAt ≤ updatedAt</li>
 * </ul>
 */
public class Hotel {

    private final HotelId id;
    private HotelName name;
    private HotelAddress address;
    private StarRating starRating;
    private final EnumSet<Amenity> amenities;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Hotel(HotelId id,
                  HotelName name,
                  HotelAddress address,
                  StarRating starRating,
                  Set<Amenity> amenities,
                  long version,
                  Instant createdAt,
                  Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.address = Objects.requireNonNull(address, "address");
        this.starRating = Objects.requireNonNull(starRating, "starRating");
        this.amenities = amenities == null || amenities.isEmpty()
            ? EnumSet.noneOf(Amenity.class)
            : EnumSet.copyOf(amenities);
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 신규 Hotel 생성. id 는 내부에서 UUID v7 로 발급한다. */
    public static Hotel create(HotelName name,
                               HotelAddress address,
                               StarRating starRating,
                               Set<Amenity> amenities,
                               Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new Hotel(HotelId.newId(), name, address, starRating, amenities, 0L, now, now);
    }

    /** 영속 저장소에서 복원. 외부(Mapper) 가 보유 중인 상태 그대로 주입. */
    public static Hotel restore(HotelId id,
                                HotelName name,
                                HotelAddress address,
                                StarRating starRating,
                                Set<Amenity> amenities,
                                long version,
                                Instant createdAt,
                                Instant updatedAt) {
        return new Hotel(id, name, address, starRating, amenities, version, createdAt, updatedAt);
    }

    public void rename(HotelName newName, Clock clock) {
        Objects.requireNonNull(newName, "newName");
        this.name = newName;
        this.updatedAt = Instant.now(clock);
    }

    public void relocate(HotelAddress newAddress, Clock clock) {
        Objects.requireNonNull(newAddress, "newAddress");
        this.address = newAddress;
        this.updatedAt = Instant.now(clock);
    }

    public void changeStarRating(StarRating newRating, Clock clock) {
        Objects.requireNonNull(newRating, "newRating");
        this.starRating = newRating;
        this.updatedAt = Instant.now(clock);
    }

    public void replaceAmenities(Set<Amenity> newAmenities, Clock clock) {
        Objects.requireNonNull(newAmenities, "newAmenities");
        this.amenities.clear();
        this.amenities.addAll(newAmenities);
        this.updatedAt = Instant.now(clock);
    }

    public HotelId id() {
        return id;
    }

    public HotelName name() {
        return name;
    }

    public HotelAddress address() {
        return address;
    }

    public StarRating starRating() {
        return starRating;
    }

    /** 외부에 공개되는 방어적 복사본. */
    public Set<Amenity> amenities() {
        return EnumSet.copyOf(amenities);
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

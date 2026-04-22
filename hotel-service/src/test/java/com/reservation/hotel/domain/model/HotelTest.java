package com.reservation.hotel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotelTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("create: createdAt == updatedAt, version 은 0")
    void createInitialState() {
        Hotel hotel = newHotel();

        assertThat(hotel.createdAt()).isEqualTo(FIXED.instant());
        assertThat(hotel.updatedAt()).isEqualTo(FIXED.instant());
        assertThat(hotel.version()).isZero();
        assertThat(hotel.amenities()).containsExactlyInAnyOrder(Amenity.WIFI, Amenity.PARKING);
    }

    @Test
    @DisplayName("amenities() 는 방어적 복사 — 외부 수정이 내부 Set 에 영향 없음")
    void amenitiesReturnsDefensiveCopy() {
        Hotel hotel = newHotel();
        Set<Amenity> returned = hotel.amenities();

        returned.remove(Amenity.WIFI);

        assertThat(hotel.amenities()).contains(Amenity.WIFI);
    }

    @Test
    @DisplayName("rename: 이름이 바뀌면 updatedAt 이 갱신된다")
    void renameUpdatesTimestamp() {
        Clock later = Clock.fixed(FIXED.instant().plusSeconds(60), ZoneOffset.UTC);
        Hotel hotel = newHotel();

        hotel.rename(new HotelName("Hotel B"), later);

        assertThat(hotel.name().value()).isEqualTo("Hotel B");
        assertThat(hotel.updatedAt()).isEqualTo(later.instant());
        assertThat(hotel.createdAt()).isEqualTo(FIXED.instant());
    }

    @Test
    @DisplayName("replaceAmenities: null 입력 거부 — 비우려면 Set.of() 를 넘겨야")
    void replaceAmenitiesRejectsNull() {
        Hotel hotel = newHotel();
        assertThatThrownBy(() -> hotel.replaceAmenities(null, FIXED))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("replaceAmenities(Set.of()) 로 비우기")
    void replaceAmenitiesWithEmptySet() {
        Hotel hotel = newHotel();
        hotel.replaceAmenities(Set.of(), FIXED);
        assertThat(hotel.amenities()).isEmpty();
    }

    @Test
    @DisplayName("changeStarRating: null 입력 거부")
    void changeStarRatingRejectsNull() {
        Hotel hotel = newHotel();
        assertThatThrownBy(() -> hotel.changeStarRating(null, FIXED))
            .isInstanceOf(NullPointerException.class);
    }

    private static Hotel newHotel() {
        return Hotel.create(
            new HotelName("Hotel A"),
            new HotelAddress("1 Street", "Seoul", "KR"),
            new StarRating(5),
            EnumSet.of(Amenity.WIFI, Amenity.PARKING),
            FIXED
        );
    }
}

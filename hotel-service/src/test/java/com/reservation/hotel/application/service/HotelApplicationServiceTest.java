package com.reservation.hotel.application.service;

import com.reservation.hotel.application.dto.HotelResult;
import com.reservation.hotel.application.dto.RegisterHotelCommand;
import com.reservation.hotel.domain.exception.HotelNotFoundException;
import com.reservation.hotel.domain.model.Hotel;
import com.reservation.hotel.domain.model.HotelAddress;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.HotelName;
import com.reservation.hotel.domain.model.StarRating;
import com.reservation.hotel.domain.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotelApplicationServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC);

    private HotelRepository repository;
    private HotelApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(HotelRepository.class);
        service = new HotelApplicationService(repository, FIXED);
    }

    @Test
    @DisplayName("register: 저장 후 Result 반환, save 는 1회 호출")
    void registerSavesAndReturnsResult() {
        when(repository.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));

        HotelResult result = service.register(new RegisterHotelCommand(
            "Hotel A", "1 Street", "Seoul", "KR", 5, Set.of("WIFI", "PARKING")
        ));

        assertThat(result.id()).isNotBlank();
        assertThat(result.amenities()).containsExactlyInAnyOrder("WIFI", "PARKING");
        verify(repository).save(any(Hotel.class));
    }

    @Test
    @DisplayName("register: amenities 가 null 또는 빈 Set 도 허용")
    void registerWithoutAmenities() {
        when(repository.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));

        HotelResult result = service.register(new RegisterHotelCommand(
            "Hotel A", "1 Street", "Seoul", "KR", 3, null
        ));

        assertThat(result.amenities()).isEmpty();
    }

    @Test
    @DisplayName("register: 알 수 없는 amenity 문자열은 IllegalArgumentException")
    void registerRejectsUnknownAmenity() {
        assertThatThrownBy(() -> service.register(new RegisterHotelCommand(
            "Hotel A", "1 Street", "Seoul", "KR", 3, Set.of("UNKNOWN_AMENITY")
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findById: 미존재 시 HotelNotFoundException")
    void findByIdThrowsWhenMissing() {
        HotelId id = HotelId.newId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id.asString()))
            .isInstanceOf(HotelNotFoundException.class);
    }

    @Test
    @DisplayName("findById: 존재 시 결과 매핑")
    void findByIdReturnsResult() {
        HotelId id = HotelId.newId();
        Hotel hotel = Hotel.restore(
            id, new HotelName("H"), new HotelAddress("S", "Seoul", "KR"),
            new StarRating(4), EnumSet.of(com.reservation.hotel.domain.model.Amenity.WIFI),
            1L, FIXED.instant(), FIXED.instant()
        );
        when(repository.findById(id)).thenReturn(Optional.of(hotel));

        HotelResult result = service.findById(id.asString());

        assertThat(result.id()).isEqualTo(id.asString());
        assertThat(result.amenities()).containsExactly("WIFI");
    }
}

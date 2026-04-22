package com.reservation.hotel.application.service;

import com.reservation.hotel.application.dto.HotelResult;
import com.reservation.hotel.application.dto.RegisterHotelCommand;
import com.reservation.hotel.domain.exception.HotelNotFoundException;
import com.reservation.hotel.domain.model.Amenity;
import com.reservation.hotel.domain.model.Hotel;
import com.reservation.hotel.domain.model.HotelAddress;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.HotelName;
import com.reservation.hotel.domain.model.StarRating;
import com.reservation.hotel.domain.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hotel Aggregate 생성 · 조회를 담당하는 Use Case 진입점.
 *
 * <p>본 Phase 에서는 Hotel 에 대한 이벤트 발행이 불필요(구독자 없음) 하여 생성/수정 시
 * 이벤트를 남기지 않는다. Room 변경은 {@code RoomApplicationService} 가 발행한다.
 */
@Service
public class HotelApplicationService {

    private final HotelRepository hotelRepository;
    private final Clock clock;

    public HotelApplicationService(HotelRepository hotelRepository, Clock clock) {
        this.hotelRepository = Objects.requireNonNull(hotelRepository, "hotelRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public HotelResult register(RegisterHotelCommand command) {
        Objects.requireNonNull(command, "command");
        Hotel hotel = Hotel.create(
            new HotelName(command.name()),
            new HotelAddress(command.addressStreet(), command.addressCity(), command.addressCountry()),
            new StarRating(command.starRating()),
            toAmenities(command.amenities()),
            clock
        );
        Hotel saved = hotelRepository.save(hotel);
        return HotelResult.of(saved);
    }

    @Transactional(readOnly = true)
    public HotelResult findById(String hotelId) {
        Objects.requireNonNull(hotelId, "hotelId");
        HotelId id = HotelId.of(hotelId);
        Hotel hotel = hotelRepository.findById(id)
            .orElseThrow(() -> new HotelNotFoundException(id));
        return HotelResult.of(hotel);
    }

    private Set<Amenity> toAmenities(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EnumSet.noneOf(Amenity.class);
        }
        return raw.stream().map(Amenity::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}

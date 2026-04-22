package com.reservation.hotel.application.service;

import com.reservation.hotel.application.dto.RegisterRoomTypeCommand;
import com.reservation.hotel.application.dto.RoomTypeResult;
import com.reservation.hotel.domain.exception.DuplicateRoomTypeNameException;
import com.reservation.hotel.domain.exception.HotelNotFoundException;
import com.reservation.hotel.domain.exception.RoomTypeNotFoundException;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.MaxOccupancy;
import com.reservation.hotel.domain.model.RoomType;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.domain.model.RoomTypeName;
import com.reservation.hotel.domain.repository.HotelRepository;
import com.reservation.hotel.domain.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoomTypeApplicationServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC);

    private RoomTypeRepository roomTypeRepository;
    private HotelRepository hotelRepository;
    private RoomTypeApplicationService service;

    @BeforeEach
    void setUp() {
        roomTypeRepository = mock(RoomTypeRepository.class);
        hotelRepository = mock(HotelRepository.class);
        service = new RoomTypeApplicationService(roomTypeRepository, hotelRepository, FIXED);
    }

    @Test
    @DisplayName("register: Hotel 이 없으면 HotelNotFoundException, save 호출 없음")
    void registerHotelMissing() {
        HotelId hotelId = HotelId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> service.register(new RegisterRoomTypeCommand(hotelId.asString(), "Standard", 2)))
            .isInstanceOf(HotelNotFoundException.class);
        verifyNoInteractions(roomTypeRepository);
    }

    @Test
    @DisplayName("register: 이름 중복 시 DuplicateRoomTypeNameException, save 호출 없음")
    void registerDuplicateName() {
        HotelId hotelId = HotelId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.existsByHotelIdAndName(eq(hotelId), any(RoomTypeName.class))).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRoomTypeCommand(hotelId.asString(), "Standard", 2)))
            .isInstanceOf(DuplicateRoomTypeNameException.class);
        verify(roomTypeRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("register: 정상 저장 후 Result 반환")
    void registerSuccess() {
        HotelId hotelId = HotelId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.existsByHotelIdAndName(eq(hotelId), any(RoomTypeName.class))).thenReturn(false);
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomTypeResult result = service.register(new RegisterRoomTypeCommand(hotelId.asString(), "Standard", 2));

        assertThat(result.hotelId()).isEqualTo(hotelId.asString());
        assertThat(result.name()).isEqualTo("Standard");
        assertThat(result.maxOccupancy()).isEqualTo(2);
    }

    @Test
    @DisplayName("findById: 미존재 시 RoomTypeNotFoundException")
    void findByIdMissing() {
        RoomTypeId id = RoomTypeId.newId();
        when(roomTypeRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id.asString()))
            .isInstanceOf(RoomTypeNotFoundException.class);
    }

    @Test
    @DisplayName("listByHotel: Hotel 이 없으면 HotelNotFoundException")
    void listByHotelMissingHotel() {
        HotelId hotelId = HotelId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(false);
        assertThatThrownBy(() -> service.listByHotel(hotelId.asString()))
            .isInstanceOf(HotelNotFoundException.class);
    }

    @Test
    @DisplayName("listByHotel: 결과를 Result 로 매핑")
    void listByHotelReturnsMapped() {
        HotelId hotelId = HotelId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        RoomType rt = RoomType.create(hotelId, new RoomTypeName("Standard"), new MaxOccupancy(2), FIXED);
        when(roomTypeRepository.findByHotelId(hotelId)).thenReturn(List.of(rt));

        List<RoomTypeResult> results = service.listByHotel(hotelId.asString());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).hotelId()).isEqualTo(hotelId.asString());
    }
}

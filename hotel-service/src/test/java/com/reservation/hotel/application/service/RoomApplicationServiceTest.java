package com.reservation.hotel.application.service;

import com.reservation.common.messaging.outbox.OutboxEventPublisher;
import com.reservation.contracts.event.DomainEvent;
import com.reservation.contracts.event.hotel.RoomCreatedEvent;
import com.reservation.contracts.event.hotel.RoomDeletedEvent;
import com.reservation.contracts.event.hotel.RoomUpdatedEvent;
import com.reservation.hotel.application.dto.RegisterRoomCommand;
import com.reservation.hotel.application.dto.RoomResult;
import com.reservation.hotel.application.dto.UpdateRoomCommand;
import com.reservation.hotel.domain.exception.DuplicateRoomNumberException;
import com.reservation.hotel.domain.exception.HotelNotFoundException;
import com.reservation.hotel.domain.exception.RoomNotFoundException;
import com.reservation.hotel.domain.exception.RoomTypeHotelMismatchException;
import com.reservation.hotel.domain.exception.RoomTypeNotFoundException;
import com.reservation.hotel.domain.model.Floor;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.MaxOccupancy;
import com.reservation.hotel.domain.model.Room;
import com.reservation.hotel.domain.model.RoomId;
import com.reservation.hotel.domain.model.RoomNumber;
import com.reservation.hotel.domain.model.RoomStatus;
import com.reservation.hotel.domain.model.RoomType;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.domain.model.RoomTypeName;
import com.reservation.hotel.domain.repository.HotelRepository;
import com.reservation.hotel.domain.repository.RoomRepository;
import com.reservation.hotel.domain.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomApplicationServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC);
    private static final String TOPIC = "hotel-events";

    private RoomRepository roomRepository;
    private RoomTypeRepository roomTypeRepository;
    private HotelRepository hotelRepository;
    private OutboxEventPublisher outbox;
    private RoomApplicationService service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        roomTypeRepository = mock(RoomTypeRepository.class);
        hotelRepository = mock(HotelRepository.class);
        outbox = mock(OutboxEventPublisher.class);
        service = new RoomApplicationService(roomRepository, roomTypeRepository, hotelRepository, outbox, FIXED);
    }

    @Test
    @DisplayName("register: 성공 시 RoomCreatedEvent 를 outbox 에 저장")
    void registerPublishesCreatedEvent() {
        HotelId hotelId = HotelId.newId();
        RoomTypeId roomTypeId = RoomTypeId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findById(roomTypeId))
            .thenReturn(Optional.of(roomType(roomTypeId, hotelId, "Standard")));
        when(roomRepository.existsByHotelIdAndFloorAndNumber(any(), any(), any())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResult result = service.register(
            new RegisterRoomCommand(hotelId.asString(), roomTypeId.asString(), 3, "301"));

        assertThat(result.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(eventCaptor.capture(), eq(TOPIC), eq(hotelId.asString()));
        assertThat(eventCaptor.getValue()).isInstanceOf(RoomCreatedEvent.class);
        RoomCreatedEvent created = (RoomCreatedEvent) eventCaptor.getValue();
        assertThat(created.hotelId()).isEqualTo(hotelId.asString());
        assertThat(created.roomTypeId()).isEqualTo(roomTypeId.asString());
    }

    @Test
    @DisplayName("register: Hotel 없으면 HotelNotFoundException + 이벤트 발행 없음")
    void registerHotelMissing() {
        HotelId hotelId = HotelId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> service.register(
            new RegisterRoomCommand(hotelId.asString(), RoomTypeId.newId().asString(), 3, "301")))
            .isInstanceOf(HotelNotFoundException.class);
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("register: RoomType 이 다른 호텔 소속이면 RoomTypeHotelMismatchException (409 계열)")
    void registerRejectsCrossHotelRoomType() {
        HotelId hotelId = HotelId.newId();
        HotelId otherHotel = HotelId.newId();
        RoomTypeId roomTypeId = RoomTypeId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findById(roomTypeId))
            .thenReturn(Optional.of(roomType(roomTypeId, otherHotel, "Standard")));

        assertThatThrownBy(() -> service.register(
            new RegisterRoomCommand(hotelId.asString(), roomTypeId.asString(), 3, "301")))
            .isInstanceOf(RoomTypeHotelMismatchException.class);
    }

    @Test
    @DisplayName("register: 중복 번호는 DuplicateRoomNumberException + 이벤트 없음")
    void registerRejectsDuplicateNumber() {
        HotelId hotelId = HotelId.newId();
        RoomTypeId roomTypeId = RoomTypeId.newId();
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findById(roomTypeId))
            .thenReturn(Optional.of(roomType(roomTypeId, hotelId, "Standard")));
        when(roomRepository.existsByHotelIdAndFloorAndNumber(eq(hotelId), any(Floor.class), any(RoomNumber.class)))
            .thenReturn(true);

        assertThatThrownBy(() -> service.register(
            new RegisterRoomCommand(hotelId.asString(), roomTypeId.asString(), 3, "301")))
            .isInstanceOf(DuplicateRoomNumberException.class);
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("updateRoomType: 저장 후 RoomUpdatedEvent 발행")
    void updateRoomTypePublishesUpdatedEvent() {
        HotelId hotelId = HotelId.newId();
        RoomTypeId oldType = RoomTypeId.newId();
        RoomTypeId newType = RoomTypeId.newId();
        RoomId roomId = RoomId.newId();
        Room room = Room.restore(roomId, hotelId, oldType,
            new Floor(3), new RoomNumber("301"), RoomStatus.ACTIVE,
            0L, FIXED.instant(), FIXED.instant());
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomTypeRepository.findById(newType))
            .thenReturn(Optional.of(roomType(newType, hotelId, "Deluxe")));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateRoomType(new UpdateRoomCommand(roomId.asString(), newType.asString()));

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(eventCaptor.capture(), eq(TOPIC), eq(hotelId.asString()));
        assertThat(eventCaptor.getValue()).isInstanceOf(RoomUpdatedEvent.class);
    }

    @Test
    @DisplayName("deactivate: DEACTIVATED 전이 후 RoomDeletedEvent 발행")
    void deactivatePublishesDeletedEvent() {
        HotelId hotelId = HotelId.newId();
        RoomTypeId roomTypeId = RoomTypeId.newId();
        RoomId roomId = RoomId.newId();
        Room room = Room.restore(roomId, hotelId, roomTypeId,
            new Floor(3), new RoomNumber("301"), RoomStatus.ACTIVE,
            0L, FIXED.instant(), FIXED.instant());
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(roomId.asString());

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(eventCaptor.capture(), eq(TOPIC), eq(hotelId.asString()));
        assertThat(eventCaptor.getValue()).isInstanceOf(RoomDeletedEvent.class);
    }

    @Test
    @DisplayName("deactivate: 이미 DEACTIVATED 면 멱등 — 이벤트 발행 없음 · save 없음")
    void deactivateIdempotent() {
        HotelId hotelId = HotelId.newId();
        RoomTypeId roomTypeId = RoomTypeId.newId();
        RoomId roomId = RoomId.newId();
        Room room = Room.restore(roomId, hotelId, roomTypeId,
            new Floor(3), new RoomNumber("301"), RoomStatus.DEACTIVATED,
            1L, FIXED.instant(), FIXED.instant());
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        service.deactivate(roomId.asString());

        verify(outbox, never()).publish(any(), any(), any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate: Room 미존재 시 RoomNotFoundException")
    void deactivateRejectsMissingRoom() {
        RoomId roomId = RoomId.newId();
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deactivate(roomId.asString()))
            .isInstanceOf(RoomNotFoundException.class);
    }

    private static RoomType roomType(RoomTypeId id, HotelId hotelId, String name) {
        return RoomType.restore(id, hotelId, new RoomTypeName(name), new MaxOccupancy(2),
            0L, FIXED.instant(), FIXED.instant());
    }
}

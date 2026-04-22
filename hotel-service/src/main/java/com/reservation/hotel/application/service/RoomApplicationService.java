package com.reservation.hotel.application.service;

import com.reservation.common.domain.UuidV7;
import com.reservation.common.messaging.outbox.OutboxEventPublisher;
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
import com.reservation.hotel.domain.model.Room;
import com.reservation.hotel.domain.model.RoomId;
import com.reservation.hotel.domain.model.RoomNumber;
import com.reservation.hotel.domain.model.RoomType;
import com.reservation.hotel.domain.model.RoomTypeId;
import com.reservation.hotel.domain.repository.HotelRepository;
import com.reservation.hotel.domain.repository.RoomRepository;
import com.reservation.hotel.domain.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Room Aggregate Use Case. 생성 · 수정 · 삭제마다 {@code hotel-events} 토픽으로 이벤트를
 * 발행해 reservation-service 의 Inventory 초기화 / 정리를 유도한다 (FR-H-04 · FR-H-05).
 *
 * <p>상태 전이 규칙:
 * <ul>
 *   <li>"삭제" 는 물리 DELETE 가 아닌 {@link Room#deactivate} (soft-delete). 기존 예약
 *       이력 · 감사 로그를 잃지 않고 reservation-service 에는 {@code RoomDeletedEvent}
 *       를 발행해 inventory 를 제거하게 한다.</li>
 *   <li>이벤트 저장(Outbox) 은 Room 저장과 같은 로컬 트랜잭션에서 커밋되어 atomicity
 *       가 보장된다. 실제 Kafka 전송은 {@code OutboxRelay} 가 스케줄 폴링.</li>
 * </ul>
 *
 * <p>Kafka 토픽명은 Phase 1 PR-1.1a 에서 확정한 {@code hotel-events}. 파티션 키는
 * code-planning Q4 답안대로 {@code hotelId} 로 고정 — 동일 호텔의 room 이벤트 순서
 * 보장 (consumer 측 Redis 캐시 일관성).
 */
@Service
public class RoomApplicationService {

    private static final String HOTEL_EVENTS_TOPIC = "hotel-events";

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    public RoomApplicationService(RoomRepository roomRepository,
                                  RoomTypeRepository roomTypeRepository,
                                  HotelRepository hotelRepository,
                                  OutboxEventPublisher outboxEventPublisher,
                                  Clock clock) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository");
        this.roomTypeRepository = Objects.requireNonNull(roomTypeRepository, "roomTypeRepository");
        this.hotelRepository = Objects.requireNonNull(hotelRepository, "hotelRepository");
        this.outboxEventPublisher = Objects.requireNonNull(outboxEventPublisher, "outboxEventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public RoomResult register(RegisterRoomCommand command) {
        Objects.requireNonNull(command, "command");
        HotelId hotelId = HotelId.of(command.hotelId());
        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        RoomTypeId roomTypeId = RoomTypeId.of(command.roomTypeId());
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
            .orElseThrow(() -> new RoomTypeNotFoundException(roomTypeId));
        if (!roomType.hotelId().equals(hotelId)) {
            throw new RoomTypeHotelMismatchException(hotelId, roomTypeId, roomType.hotelId());
        }
        Floor floor = new Floor(command.floor());
        RoomNumber number = new RoomNumber(command.number());
        if (roomRepository.existsByHotelIdAndFloorAndNumber(hotelId, floor, number)) {
            throw new DuplicateRoomNumberException(hotelId, floor, number);
        }
        Instant now = Instant.now(clock);
        Room room = Room.create(hotelId, roomTypeId, floor, number, Clock.fixed(now, clock.getZone()));
        Room saved = roomRepository.save(room);

        outboxEventPublisher.publish(
            new RoomCreatedEvent(UuidV7.create(), now,
                saved.hotelId().asString(), saved.id().asString(), saved.roomTypeId().asString()),
            HOTEL_EVENTS_TOPIC,
            saved.hotelId().asString()
        );
        return RoomResult.of(saved);
    }

    @Transactional
    public RoomResult updateRoomType(UpdateRoomCommand command) {
        Objects.requireNonNull(command, "command");
        RoomId roomId = RoomId.of(command.roomId());
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));

        RoomTypeId newRoomTypeId = RoomTypeId.of(command.roomTypeId());
        RoomType newRoomType = roomTypeRepository.findById(newRoomTypeId)
            .orElseThrow(() -> new RoomTypeNotFoundException(newRoomTypeId));
        if (!newRoomType.hotelId().equals(room.hotelId())) {
            throw new RoomTypeHotelMismatchException(room.hotelId(), newRoomTypeId, newRoomType.hotelId());
        }

        Instant now = Instant.now(clock);
        room.reassignRoomType(newRoomTypeId, Clock.fixed(now, clock.getZone()));
        Room saved = roomRepository.save(room);

        outboxEventPublisher.publish(
            new RoomUpdatedEvent(UuidV7.create(), now,
                saved.hotelId().asString(), saved.id().asString(), saved.roomTypeId().asString()),
            HOTEL_EVENTS_TOPIC,
            saved.hotelId().asString()
        );
        return RoomResult.of(saved);
    }

    /**
     * 객실 soft-delete. 이미 DEACTIVATED 인 경우 이벤트를 재발행하지 않고 멱등 처리.
     *
     * <p>활성 예약의 존재 여부 검증은 hotel-service 책임 밖이다. reservation-service
     * 가 {@link RoomDeletedEvent} 를 수신해 inventory 조정 시 자체 정책에 따라 처리
     * 한다 (PRD FR-RSV-04 후속 PR). hotel-service 는 소유하지 않은 데이터를 참조하지
     * 않는다 (CLAUDE.md 원칙 #2 Database per Service).
     */
    @Transactional
    public void deactivate(String roomId) {
        Objects.requireNonNull(roomId, "roomId");
        RoomId id = RoomId.of(roomId);
        Room room = roomRepository.findById(id)
            .orElseThrow(() -> new RoomNotFoundException(id));
        if (room.status().isDeactivated()) {
            return;
        }
        Instant now = Instant.now(clock);
        room.deactivate(Clock.fixed(now, clock.getZone()));
        Room saved = roomRepository.save(room);

        outboxEventPublisher.publish(
            new RoomDeletedEvent(UuidV7.create(), now,
                saved.hotelId().asString(), saved.id().asString(), saved.roomTypeId().asString()),
            HOTEL_EVENTS_TOPIC,
            saved.hotelId().asString()
        );
    }

    @Transactional(readOnly = true)
    public RoomResult findById(String roomId) {
        Objects.requireNonNull(roomId, "roomId");
        RoomId id = RoomId.of(roomId);
        return RoomResult.of(roomRepository.findById(id)
            .orElseThrow(() -> new RoomNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public List<RoomResult> listByHotel(String hotelId) {
        Objects.requireNonNull(hotelId, "hotelId");
        HotelId id = HotelId.of(hotelId);
        if (!hotelRepository.existsById(id)) {
            throw new HotelNotFoundException(id);
        }
        return roomRepository.findByHotelId(id).stream()
            .map(RoomResult::of)
            .toList();
    }
}

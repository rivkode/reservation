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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * RoomType Aggregate Use Case. 이름 UNIQUE 선제 검증 + DB 레벨 재확인으로 race
 * condition 을 이중 방어한다. RoomType 삭제는 본 PR 범위 외 (PRD FR 명시 없음).
 */
@Service
public class RoomTypeApplicationService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final Clock clock;

    public RoomTypeApplicationService(RoomTypeRepository roomTypeRepository,
                                      HotelRepository hotelRepository,
                                      Clock clock) {
        this.roomTypeRepository = Objects.requireNonNull(roomTypeRepository, "roomTypeRepository");
        this.hotelRepository = Objects.requireNonNull(hotelRepository, "hotelRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public RoomTypeResult register(RegisterRoomTypeCommand command) {
        Objects.requireNonNull(command, "command");
        HotelId hotelId = HotelId.of(command.hotelId());
        if (!hotelRepository.existsById(hotelId)) {
            throw new HotelNotFoundException(hotelId);
        }
        RoomTypeName name = new RoomTypeName(command.name());
        if (roomTypeRepository.existsByHotelIdAndName(hotelId, name)) {
            throw new DuplicateRoomTypeNameException(hotelId, name);
        }
        RoomType roomType = RoomType.create(hotelId, name, new MaxOccupancy(command.maxOccupancy()), clock);
        return RoomTypeResult.of(roomTypeRepository.save(roomType));
    }

    @Transactional(readOnly = true)
    public RoomTypeResult findById(String roomTypeId) {
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        RoomTypeId id = RoomTypeId.of(roomTypeId);
        RoomType roomType = roomTypeRepository.findById(id)
            .orElseThrow(() -> new RoomTypeNotFoundException(id));
        return RoomTypeResult.of(roomType);
    }

    @Transactional(readOnly = true)
    public List<RoomTypeResult> listByHotel(String hotelId) {
        Objects.requireNonNull(hotelId, "hotelId");
        HotelId id = HotelId.of(hotelId);
        if (!hotelRepository.existsById(id)) {
            throw new HotelNotFoundException(id);
        }
        return roomTypeRepository.findByHotelId(id).stream()
            .map(RoomTypeResult::of)
            .toList();
    }
}

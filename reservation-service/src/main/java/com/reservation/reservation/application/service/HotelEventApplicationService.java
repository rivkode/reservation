package com.reservation.reservation.application.service;

import com.reservation.contracts.event.hotel.RoomCreatedEvent;
import com.reservation.contracts.event.hotel.RoomDeletedEvent;
import com.reservation.contracts.event.hotel.RoomUpdatedEvent;
import com.reservation.reservation.application.idempotency.ProcessedEventStore;
import com.reservation.reservation.domain.exception.InvalidInventoryOperationException;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.RoomAssignment;
import com.reservation.reservation.domain.model.RoomId;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.repository.RoomAssignmentRepository;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * hotel-events 수신 처리의 orchestrator.
 *
 * <p>CLAUDE.md 의 "과도한 선제 추상화 금지" 원칙에 따라 날짜 범위 루프 · Repository fan-out ·
 * 트랜잭션 경계 같은 조율(orchestration) 로직은 Application Service 에 둔다 (ddd-architect
 * High-2 반영). 도메인 규칙은 Aggregate · VO 가 직접 표현한다.
 *
 * <p>각 public 메서드는 단일 이벤트 하나를 한 로컬 트랜잭션으로 처리한다. 트랜잭션 범위에
 * (Inventory 90일 row 갱신) + (RoomAssignment 저장/삭제) + (processed_events 기록) 이 들어가
 * 부분 실패가 남지 않는다. at-least-once 재전송은 {@link ProcessedEventStore} 가 흡수.
 *
 * <p>이벤트 순서 역전 방어 — Kafka 파티션 내 순서는 보장되지만 상위 비즈니스 순서 보장은
 * 없다. 아래 규칙으로 흡수:
 * <ul>
 *   <li>{@code RoomUpdated} 인데 {@link RoomAssignment} 가 없으면 → 매핑 생성 + 새 타입 +1 만
 *       수행 (이전 타입 없음)</li>
 *   <li>{@code RoomDeleted} 인데 매핑이 없으면 → warn 로그 + processed 기록만 (NO-OP)</li>
 *   <li>{@link InvalidInventoryOperationException} 발생 날짜는 warn 로그 후 해당 날짜만 skip
 *       — tombstone row 재전송 허용과 맞물린다</li>
 * </ul>
 */
@Slf4j
@Service
public class HotelEventApplicationService {

    private final RoomTypeInventoryRepository inventoryRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final ProcessedEventStore processedEventStore;
    private final Clock clock;

    /**
     * Inventory 선제 생성 범위 (일). {@code application-*.yml} 의
     * {@code app.inventory.horizon-days} 로 제어. 기본 90 (PRD Q7 캐시 범위와 정합).
     */
    private final int horizonDays;

    public HotelEventApplicationService(RoomTypeInventoryRepository inventoryRepository,
                                        RoomAssignmentRepository assignmentRepository,
                                        ProcessedEventStore processedEventStore,
                                        Clock clock,
                                        @Value("${app.inventory.horizon-days:90}") int horizonDays) {
        this.inventoryRepository = inventoryRepository;
        this.assignmentRepository = assignmentRepository;
        this.processedEventStore = processedEventStore;
        this.clock = clock;
        if (horizonDays <= 0) {
            throw new IllegalArgumentException(
                "app.inventory.horizon-days must be positive, was " + horizonDays);
        }
        this.horizonDays = horizonDays;
    }

    @Transactional
    public void onRoomCreated(RoomCreatedEvent event) {
        Objects.requireNonNull(event, "event");
        if (processedEventStore.isAlreadyProcessed(event.eventId())) {
            log.debug("Skip already-processed RoomCreatedEvent eventId={}", event.eventId());
            return;
        }

        HotelId hotelId = HotelId.of(event.hotelId());
        RoomTypeId roomTypeId = RoomTypeId.of(event.roomTypeId());
        RoomId roomId = RoomId.of(event.roomId());

        Optional<RoomAssignment> existingAssignment = assignmentRepository.findByRoomId(roomId);
        if (existingAssignment.isPresent()) {
            log.warn("RoomCreatedEvent for already-mapped roomId={} — treat as noop but mark processed."
                + " eventId={}", roomId.asString(), event.eventId());
        } else {
            assignmentRepository.save(RoomAssignment.create(roomId, hotelId, roomTypeId, clock));
            applyAddRoomOverHorizon(hotelId, roomTypeId);
        }

        markProcessed(event.eventId(), RoomCreatedEvent.class.getSimpleName());
    }

    @Transactional
    public void onRoomUpdated(RoomUpdatedEvent event) {
        Objects.requireNonNull(event, "event");
        if (processedEventStore.isAlreadyProcessed(event.eventId())) {
            log.debug("Skip already-processed RoomUpdatedEvent eventId={}", event.eventId());
            return;
        }

        HotelId hotelId = HotelId.of(event.hotelId());
        RoomTypeId newRoomTypeId = RoomTypeId.of(event.roomTypeId());
        RoomId roomId = RoomId.of(event.roomId());

        Optional<RoomAssignment> maybeAssignment = assignmentRepository.findByRoomId(roomId);
        if (maybeAssignment.isEmpty()) {
            log.warn("RoomUpdatedEvent before RoomCreated for roomId={} — creating mapping and"
                + " adding to new type only. eventId={}", roomId.asString(), event.eventId());
            assignmentRepository.save(RoomAssignment.create(roomId, hotelId, newRoomTypeId, clock));
            applyAddRoomOverHorizon(hotelId, newRoomTypeId);
        } else {
            RoomAssignment assignment = maybeAssignment.get();
            Optional<RoomTypeId> previousType = assignment.reassign(newRoomTypeId, clock);
            if (previousType.isPresent()) {
                applyRemoveRoomOverHorizon(hotelId, previousType.get());
                applyAddRoomOverHorizon(hotelId, newRoomTypeId);
                assignmentRepository.save(assignment);
            } else {
                // 동일 타입 재이벤트 — assignment 상태 변경 없음. processed 기록만 남기고 스킵.
            }
        }

        markProcessed(event.eventId(), RoomUpdatedEvent.class.getSimpleName());
    }

    @Transactional
    public void onRoomDeleted(RoomDeletedEvent event) {
        Objects.requireNonNull(event, "event");
        if (processedEventStore.isAlreadyProcessed(event.eventId())) {
            log.debug("Skip already-processed RoomDeletedEvent eventId={}", event.eventId());
            return;
        }

        RoomId roomId = RoomId.of(event.roomId());
        Optional<RoomAssignment> maybeAssignment = assignmentRepository.findByRoomId(roomId);

        if (maybeAssignment.isEmpty()) {
            log.warn("RoomDeletedEvent without prior mapping for roomId={} — noop. eventId={}",
                roomId.asString(), event.eventId());
        } else {
            RoomAssignment assignment = maybeAssignment.get();
            applyRemoveRoomOverHorizon(assignment.hotelId(), assignment.roomTypeId());
            assignmentRepository.deleteByRoomId(roomId);
        }

        markProcessed(event.eventId(), RoomDeletedEvent.class.getSimpleName());
    }

    private void applyAddRoomOverHorizon(HotelId hotelId, RoomTypeId roomTypeId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate until = today.plusDays(horizonDays);

        Map<LocalDate, RoomTypeInventory> existing = loadRange(hotelId, roomTypeId, today, until);
        List<RoomTypeInventory> batch = new ArrayList<>(horizonDays + 1);

        for (LocalDate date = today; !date.isAfter(until); date = date.plusDays(1)) {
            RoomTypeInventory inventory = existing.get(date);
            if (inventory == null) {
                inventory = RoomTypeInventory.create(hotelId, roomTypeId, date, clock);
            }
            inventory.addRoom(clock);
            batch.add(inventory);
        }
        inventoryRepository.saveAll(batch);
    }

    private void applyRemoveRoomOverHorizon(HotelId hotelId, RoomTypeId roomTypeId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate until = today.plusDays(horizonDays);

        List<RoomTypeInventory> range = inventoryRepository.findRange(hotelId, roomTypeId, today, until);
        List<RoomTypeInventory> batch = new ArrayList<>(range.size());
        for (RoomTypeInventory inventory : range) {
            try {
                inventory.removeRoom(clock);
                batch.add(inventory);
            } catch (InvalidInventoryOperationException e) {
                log.warn("Skip removeRoom on tombstone inventory {}: {}",
                    inventory.key(), e.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            inventoryRepository.saveAll(batch);
        }
    }

    private Map<LocalDate, RoomTypeInventory> loadRange(HotelId hotelId,
                                                         RoomTypeId roomTypeId,
                                                         LocalDate from,
                                                         LocalDate to) {
        List<RoomTypeInventory> list = inventoryRepository.findRange(hotelId, roomTypeId, from, to);
        Map<LocalDate, RoomTypeInventory> map = new HashMap<>(list.size() * 2);
        for (RoomTypeInventory inv : list) {
            map.put(inv.stayDate(), inv);
        }
        return map;
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventStore.markProcessed(eventId, eventType, Instant.now(clock));
    }
}

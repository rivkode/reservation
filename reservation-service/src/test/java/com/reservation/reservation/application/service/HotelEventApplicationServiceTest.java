package com.reservation.reservation.application.service;

import com.reservation.contracts.event.hotel.RoomCreatedEvent;
import com.reservation.contracts.event.hotel.RoomDeletedEvent;
import com.reservation.contracts.event.hotel.RoomUpdatedEvent;
import com.reservation.reservation.application.idempotency.ProcessedEventStore;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryCount;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.RoomAssignment;
import com.reservation.reservation.domain.model.RoomId;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.repository.RoomAssignmentRepository;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HotelEventApplicationService 단위 테스트")
class HotelEventApplicationServiceTest {

    private static final String HOTEL_UUID = "01933333-1111-7aaa-9aaa-111122223333";
    private static final String ROOM_TYPE_A_UUID = "01933333-aaaa-7aaa-9aaa-111122223333";
    private static final String ROOM_TYPE_B_UUID = "01933333-bbbb-7aaa-9aaa-111122223333";
    private static final String ROOM_UUID = "01933333-3333-7aaa-9aaa-111122223333";
    private static final Instant NOW = Instant.parse("2026-04-23T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int HORIZON_DAYS = 3; // 테스트 범위 축소 — 90일 루프 대신 4일 (today + 3)

    private RoomTypeInventoryRepository inventoryRepository;
    private RoomAssignmentRepository assignmentRepository;
    private ProcessedEventStore processedEventStore;
    private HotelEventApplicationService service;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(RoomTypeInventoryRepository.class);
        assignmentRepository = mock(RoomAssignmentRepository.class);
        processedEventStore = mock(ProcessedEventStore.class);
        when(inventoryRepository.findRange(any(), any(), any(), any())).thenReturn(List.of());
        when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.empty());
        when(processedEventStore.isAlreadyProcessed(any())).thenReturn(false);
        service = new HotelEventApplicationService(
            inventoryRepository, assignmentRepository, processedEventStore, CLOCK, HORIZON_DAYS);
    }

    @Nested
    @DisplayName("onRoomCreated")
    class OnRoomCreated {

        @Test
        @DisplayName("RoomAssignment 저장 + horizon 각 날짜에 Inventory 증가 + processed 기록")
        void creates_assignment_and_inventory() {
            RoomCreatedEvent event = new RoomCreatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);

            service.onRoomCreated(event);

            // today ~ today+3 = 4 row 를 한 번의 saveAll 로 배치 저장
            List<RoomTypeInventory> saved = captureSaveAll();
            assertThat(saved).hasSize(HORIZON_DAYS + 1)
                .allSatisfy(inv -> {
                    assertThat(inv.totalRooms()).isEqualTo(InventoryCount.of(1));
                    assertThat(inv.availableRooms()).isEqualTo(InventoryCount.of(1));
                    assertThat(inv.hotelId().asString()).isEqualTo(HOTEL_UUID);
                    assertThat(inv.roomTypeId().asString()).isEqualTo(ROOM_TYPE_A_UUID);
                })
                .extracting(RoomTypeInventory::stayDate)
                .containsExactly(
                    LocalDate.of(2026, 4, 23),
                    LocalDate.of(2026, 4, 24),
                    LocalDate.of(2026, 4, 25),
                    LocalDate.of(2026, 4, 26));

            verify(assignmentRepository).save(any(RoomAssignment.class));
            verify(processedEventStore).markProcessed(eq(event.eventId()), anyString(), any());
        }

        @Test
        @DisplayName("기존 Inventory row 가 있으면 생성 대신 addRoom — total 은 기존값 +1")
        void increments_existing_inventory() {
            LocalDate day0 = LocalDate.of(2026, 4, 23);
            RoomTypeInventory existing = RoomTypeInventory.restore(
                new InventoryKey(HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), day0),
                InventoryCount.of(5), InventoryCount.of(5), 1L, NOW, NOW);
            when(inventoryRepository.findRange(any(), any(), any(), any()))
                .thenReturn(List.of(existing));

            RoomCreatedEvent event = new RoomCreatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);
            service.onRoomCreated(event);

            assertThat(existing.totalRooms().value()).isEqualTo(6);
            assertThat(existing.availableRooms().value()).isEqualTo(6);
        }

        @Test
        @DisplayName("이미 처리된 eventId 는 skip — Repository 호출 없음")
        void skips_duplicate_event() {
            RoomCreatedEvent event = new RoomCreatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);
            when(processedEventStore.isAlreadyProcessed(event.eventId())).thenReturn(true);

            service.onRoomCreated(event);

            verify(inventoryRepository, never()).saveAll(any());
            verify(assignmentRepository, never()).save(any());
            verify(processedEventStore, never()).markProcessed(any(), anyString(), any());
        }

        @Test
        @DisplayName("같은 roomId 에 대한 이벤트 재전송 — assignment 재저장 없이 processed 만 기록")
        void noop_on_existing_assignment() {
            RoomCreatedEvent event = new RoomCreatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.of(
                RoomAssignment.create(RoomId.of(ROOM_UUID), HotelId.of(HOTEL_UUID),
                    RoomTypeId.of(ROOM_TYPE_A_UUID), CLOCK)));

            service.onRoomCreated(event);

            verify(assignmentRepository, never()).save(any());
            verify(inventoryRepository, never()).saveAll(any());
            verify(processedEventStore).markProcessed(eq(event.eventId()), anyString(), any());
        }
    }

    @Nested
    @DisplayName("onRoomUpdated")
    class OnRoomUpdated {

        @Test
        @DisplayName("타입 변경: 이전 타입 -1 batch + 새 타입 +1 batch + assignment 갱신")
        void swaps_types() {
            RoomAssignment existing = RoomAssignment.create(
                RoomId.of(ROOM_UUID), HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), CLOCK);
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.of(existing));

            Map<LocalDate, RoomTypeInventory> typeA = preloadInventory(ROOM_TYPE_A_UUID, HORIZON_DAYS);
            when(inventoryRepository.findRange(any(), eq(RoomTypeId.of(ROOM_TYPE_A_UUID)), any(), any()))
                .thenReturn(List.copyOf(typeA.values()));
            when(inventoryRepository.findRange(any(), eq(RoomTypeId.of(ROOM_TYPE_B_UUID)), any(), any()))
                .thenReturn(List.of());

            RoomUpdatedEvent event = new RoomUpdatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_B_UUID);
            service.onRoomUpdated(event);

            // 이전 타입 A: 모든 row 가 -1
            assertThat(typeA.values()).allSatisfy(inv ->
                assertThat(inv.totalRooms().value()).isEqualTo(2));

            // saveAll 이 2번 호출됨 (A 차감 batch + B 증가 batch). 각 batch 사이즈는 HORIZON_DAYS+1.
            List<List<RoomTypeInventory>> allBatches = captureAllSaveAll();
            assertThat(allBatches).hasSize(2);

            List<RoomTypeInventory> typeABatch = allBatches.stream()
                .filter(b -> !b.isEmpty() && b.get(0).roomTypeId().asString().equals(ROOM_TYPE_A_UUID))
                .findFirst().orElseThrow();
            List<RoomTypeInventory> typeBBatch = allBatches.stream()
                .filter(b -> !b.isEmpty() && b.get(0).roomTypeId().asString().equals(ROOM_TYPE_B_UUID))
                .findFirst().orElseThrow();

            assertThat(typeABatch).hasSize(HORIZON_DAYS + 1)
                .allSatisfy(inv -> assertThat(inv.totalRooms().value()).isEqualTo(2));
            assertThat(typeBBatch).hasSize(HORIZON_DAYS + 1)
                .allSatisfy(inv -> assertThat(inv.totalRooms().value()).isEqualTo(1));

            assertThat(existing.roomTypeId().asString()).isEqualTo(ROOM_TYPE_B_UUID);
            verify(assignmentRepository).save(existing);
        }

        @Test
        @DisplayName("타입 변경 중 이전 타입의 일부 날짜가 tombstone(total=0) — 그 날짜만 skip, 나머지는 정상 -1")
        void skips_tombstone_days_during_swap() {
            RoomAssignment existing = RoomAssignment.create(
                RoomId.of(ROOM_UUID), HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), CLOCK);
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.of(existing));

            // typeA 의 첫 날짜는 tombstone, 나머지는 정상 (total=3)
            LocalDate day0 = LocalDate.of(2026, 4, 23);
            RoomTypeInventory tombstone = RoomTypeInventory.restore(
                new InventoryKey(HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), day0),
                InventoryCount.zero(), InventoryCount.zero(), 1L, NOW, NOW);
            RoomTypeInventory alive1 = RoomTypeInventory.restore(
                new InventoryKey(HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), day0.plusDays(1)),
                InventoryCount.of(3), InventoryCount.of(3), 1L, NOW, NOW);
            RoomTypeInventory alive2 = RoomTypeInventory.restore(
                new InventoryKey(HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), day0.plusDays(2)),
                InventoryCount.of(3), InventoryCount.of(3), 1L, NOW, NOW);
            when(inventoryRepository.findRange(any(), eq(RoomTypeId.of(ROOM_TYPE_A_UUID)), any(), any()))
                .thenReturn(List.of(tombstone, alive1, alive2));
            when(inventoryRepository.findRange(any(), eq(RoomTypeId.of(ROOM_TYPE_B_UUID)), any(), any()))
                .thenReturn(List.of());

            RoomUpdatedEvent event = new RoomUpdatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_B_UUID);
            service.onRoomUpdated(event);

            assertThat(tombstone.totalRooms().isZero()).isTrue();
            assertThat(alive1.totalRooms().value()).isEqualTo(2);
            assertThat(alive2.totalRooms().value()).isEqualTo(2);

            // 트랜잭션은 예외 없이 커밋 가능 — processed 기록까지 도달해야 한다.
            verify(processedEventStore).markProcessed(eq(event.eventId()), anyString(), any());
            verify(assignmentRepository).save(existing);
        }

        @Test
        @DisplayName("동일 타입 재이벤트 — noop + processed 만 기록")
        void noop_on_same_type() {
            RoomAssignment existing = RoomAssignment.create(
                RoomId.of(ROOM_UUID), HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), CLOCK);
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.of(existing));

            RoomUpdatedEvent event = new RoomUpdatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);
            service.onRoomUpdated(event);

            verify(inventoryRepository, never()).saveAll(any());
            verify(assignmentRepository, never()).save(any());
            verify(processedEventStore).markProcessed(eq(event.eventId()), anyString(), any());
        }

        @Test
        @DisplayName("Assignment 없는 상태 — 이벤트 순서 역전: 매핑 생성 + 새 타입 +1")
        void handles_out_of_order_update() {
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.empty());

            RoomUpdatedEvent event = new RoomUpdatedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_B_UUID);
            service.onRoomUpdated(event);

            verify(assignmentRepository, times(1)).save(any(RoomAssignment.class));
            verify(inventoryRepository, times(1)).saveAll(any()); // 새 타입 batch 1번만
        }
    }

    @Nested
    @DisplayName("onRoomDeleted")
    class OnRoomDeleted {

        @Test
        @DisplayName("정상 삭제: horizon batch removeRoom + assignment 삭제")
        void removes_across_horizon() {
            RoomAssignment existing = RoomAssignment.create(
                RoomId.of(ROOM_UUID), HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), CLOCK);
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.of(existing));
            Map<LocalDate, RoomTypeInventory> preload = preloadInventory(ROOM_TYPE_A_UUID, HORIZON_DAYS);
            when(inventoryRepository.findRange(any(), any(), any(), any()))
                .thenReturn(List.copyOf(preload.values()));

            RoomDeletedEvent event = new RoomDeletedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);
            service.onRoomDeleted(event);

            assertThat(preload.values()).allSatisfy(inv ->
                assertThat(inv.totalRooms().value()).isEqualTo(2));
            verify(assignmentRepository).deleteByRoomId(RoomId.of(ROOM_UUID));
        }

        @Test
        @DisplayName("tombstone row (total=0) 는 warn 후 batch 에서 제외, 나머지는 정상 처리")
        void skips_tombstone_row_gracefully() {
            RoomAssignment existing = RoomAssignment.create(
                RoomId.of(ROOM_UUID), HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), CLOCK);
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.of(existing));

            LocalDate tombstoneDate = LocalDate.of(2026, 4, 23);
            RoomTypeInventory tombstone = RoomTypeInventory.restore(
                new InventoryKey(HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), tombstoneDate),
                InventoryCount.zero(), InventoryCount.zero(), 1L, NOW, NOW);
            LocalDate liveDate = LocalDate.of(2026, 4, 24);
            RoomTypeInventory live = RoomTypeInventory.restore(
                new InventoryKey(HotelId.of(HOTEL_UUID), RoomTypeId.of(ROOM_TYPE_A_UUID), liveDate),
                InventoryCount.of(2), InventoryCount.of(2), 1L, NOW, NOW);
            when(inventoryRepository.findRange(any(), any(), any(), any()))
                .thenReturn(List.of(tombstone, live));

            RoomDeletedEvent event = new RoomDeletedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);
            service.onRoomDeleted(event);

            assertThat(tombstone.totalRooms().isZero()).isTrue();
            assertThat(live.totalRooms().value()).isEqualTo(1);

            // saveAll 이 live 만 담아 호출되어야 한다.
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<RoomTypeInventory>> captor = ArgumentCaptor.forClass(List.class);
            verify(inventoryRepository).saveAll(captor.capture());
            assertThat(captor.getValue())
                .hasSize(1)
                .first()
                .satisfies(inv -> assertThat(inv.stayDate()).isEqualTo(liveDate));

            verify(processedEventStore).markProcessed(eq(event.eventId()), anyString(), any());
        }

        @Test
        @DisplayName("매핑 없는 상태 — warn + processed 기록만")
        void noop_without_mapping() {
            when(assignmentRepository.findByRoomId(any())).thenReturn(Optional.empty());

            RoomDeletedEvent event = new RoomDeletedEvent(
                UUID.randomUUID(), NOW, HOTEL_UUID, ROOM_UUID, ROOM_TYPE_A_UUID);
            service.onRoomDeleted(event);

            verify(inventoryRepository, never()).saveAll(any());
            verify(assignmentRepository, never()).deleteByRoomId(any());
            verify(processedEventStore).markProcessed(eq(event.eventId()), anyString(), any());
        }
    }

    @Nested
    @DisplayName("생성자 검증")
    class Construction {

        @Test
        @DisplayName("horizonDays ≤ 0 이면 IllegalArgument")
        void rejects_non_positive_horizon() {
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new HotelEventApplicationService(
                    inventoryRepository, assignmentRepository, processedEventStore, CLOCK, 0))
                .withMessageContaining("horizon-days");
        }
    }

    @SuppressWarnings("unchecked")
    private List<RoomTypeInventory> captureSaveAll() {
        ArgumentCaptor<List<RoomTypeInventory>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<List<RoomTypeInventory>> captureAllSaveAll() {
        ArgumentCaptor<List<RoomTypeInventory>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryRepository, atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues();
    }

    private static Map<LocalDate, RoomTypeInventory> preloadInventory(String roomTypeUuid, int horizonDays) {
        HotelId hotelId = HotelId.of(HOTEL_UUID);
        RoomTypeId roomTypeId = RoomTypeId.of(roomTypeUuid);
        LocalDate today = LocalDate.of(2026, 4, 23);
        Map<LocalDate, RoomTypeInventory> map = new HashMap<>();
        for (int i = 0; i <= horizonDays; i++) {
            LocalDate date = today.plusDays(i);
            map.put(date, RoomTypeInventory.restore(
                new InventoryKey(hotelId, roomTypeId, date),
                InventoryCount.of(3), InventoryCount.of(3), 1L, NOW, NOW));
        }
        return map;
    }
}

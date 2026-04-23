package com.reservation.reservation.domain.model;

import com.reservation.reservation.domain.exception.InsufficientInventoryException;
import com.reservation.reservation.domain.exception.InvalidInventoryOperationException;
import com.reservation.reservation.domain.exception.InventoryReleaseExceedsCapacityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("RoomTypeInventory Aggregate")
class RoomTypeInventoryTest {

    private static final HotelId HOTEL_ID = HotelId.of("01933333-1111-7aaa-9aaa-111122223333");
    private static final RoomTypeId ROOM_TYPE_ID = RoomTypeId.of("01933333-2222-7aaa-9aaa-111122223333");
    private static final LocalDate STAY_DATE = LocalDate.of(2026, 6, 1);
    private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("새 row 는 total=0, available=0 으로 시작하고 createdAt==updatedAt")
        void starts_at_zero() {
            RoomTypeInventory inv = RoomTypeInventory.create(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, CLOCK);

            assertThat(inv.totalRooms()).isEqualTo(InventoryCount.zero());
            assertThat(inv.availableRooms()).isEqualTo(InventoryCount.zero());
            assertThat(inv.version()).isZero();
            assertThat(inv.createdAt()).isEqualTo(NOW);
            assertThat(inv.updatedAt()).isEqualTo(NOW);
            assertThat(inv.key()).isEqualTo(new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE));
        }
    }

    @Nested
    @DisplayName("addRoom")
    class AddRoom {

        @Test
        @DisplayName("total +1, available +1, updatedAt 갱신")
        void increments_both() {
            RoomTypeInventory inv = RoomTypeInventory.create(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, CLOCK);
            Instant later = NOW.plusSeconds(60);
            Clock laterClock = Clock.fixed(later, ZoneOffset.UTC);

            inv.addRoom(laterClock);

            assertThat(inv.totalRooms().value()).isEqualTo(1);
            assertThat(inv.availableRooms().value()).isEqualTo(1);
            assertThat(inv.updatedAt()).isEqualTo(later);
            assertThat(inv.createdAt()).as("createdAt 은 불변").isEqualTo(NOW);
        }

        @Test
        @DisplayName("여러 번 호출하면 누적")
        void accumulates() {
            RoomTypeInventory inv = RoomTypeInventory.create(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, CLOCK);

            inv.addRoom(CLOCK);
            inv.addRoom(CLOCK);
            inv.addRoom(CLOCK);

            assertThat(inv.totalRooms().value()).isEqualTo(3);
            assertThat(inv.availableRooms().value()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("removeRoom")
    class RemoveRoom {

        @Test
        @DisplayName("total -1, available -1")
        void decrements_both() {
            RoomTypeInventory inv = inventoryWithTotal(5);

            inv.removeRoom(CLOCK);

            assertThat(inv.totalRooms().value()).isEqualTo(4);
            assertThat(inv.availableRooms().value()).isEqualTo(4);
        }

        @Test
        @DisplayName("총량 0 에서 호출하면 InvalidInventoryOperationException — tombstone 재전송 방어")
        void rejects_on_empty() {
            RoomTypeInventory inv = RoomTypeInventory.create(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE, CLOCK);

            assertThatExceptionOfType(InvalidInventoryOperationException.class)
                .isThrownBy(() -> inv.removeRoom(CLOCK))
                .withMessageContaining("empty inventory");
        }

        @Test
        @DisplayName("총량이 0 으로 감소해도 row 는 유지 (삭제는 Repository 가 하지 않음)")
        void total_can_reach_zero() {
            RoomTypeInventory inv = inventoryWithTotal(1);

            inv.removeRoom(CLOCK);

            assertThat(inv.totalRooms().isZero()).isTrue();
            assertThat(inv.availableRooms().isZero()).isTrue();
        }
    }

    @Nested
    @DisplayName("decrease (예약 차감)")
    class Decrease {

        @Test
        @DisplayName("available -1, total 은 불변, updatedAt 갱신")
        void decreases_available_only() {
            RoomTypeInventory inv = inventoryWithTotal(5);
            Instant later = NOW.plusSeconds(60);
            Clock laterClock = Clock.fixed(later, ZoneOffset.UTC);

            inv.decrease(laterClock);

            assertThat(inv.totalRooms().value()).as("totalRooms 불변").isEqualTo(5);
            assertThat(inv.availableRooms().value()).isEqualTo(4);
            assertThat(inv.updatedAt()).isEqualTo(later);
        }

        @Test
        @DisplayName("available 이 0 이면 InsufficientInventoryException — 오버부킹 방어")
        void rejects_when_no_availability() {
            RoomTypeInventory inv = RoomTypeInventory.restore(
                new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE),
                InventoryCount.of(5), InventoryCount.zero(), 0L, NOW, NOW);

            assertThatExceptionOfType(InsufficientInventoryException.class)
                .isThrownBy(() -> inv.decrease(CLOCK))
                .matches(e -> e.key().equals(inv.key()));
        }

        @Test
        @DisplayName("available 1 → 0 까지 차감 가능 (마지막 한 개)")
        void allows_until_zero() {
            RoomTypeInventory inv = RoomTypeInventory.restore(
                new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE),
                InventoryCount.of(3), InventoryCount.of(1), 0L, NOW, NOW);

            inv.decrease(CLOCK);

            assertThat(inv.availableRooms().isZero()).isTrue();
            assertThat(inv.totalRooms().value()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("release (예약 취소 복원)")
    class Release {

        @Test
        @DisplayName("available +1, total 은 불변, updatedAt 갱신")
        void releases_available_only() {
            RoomTypeInventory inv = RoomTypeInventory.restore(
                new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE),
                InventoryCount.of(5), InventoryCount.of(3), 0L, NOW, NOW);
            Instant later = NOW.plusSeconds(60);
            Clock laterClock = Clock.fixed(later, ZoneOffset.UTC);

            inv.release(laterClock);

            assertThat(inv.totalRooms().value()).as("totalRooms 불변").isEqualTo(5);
            assertThat(inv.availableRooms().value()).isEqualTo(4);
            assertThat(inv.updatedAt()).isEqualTo(later);
        }

        @Test
        @DisplayName("available 이 이미 total 과 같으면 InventoryReleaseExceedsCapacityException")
        void rejects_when_already_full() {
            RoomTypeInventory inv = inventoryWithTotal(3);

            assertThatExceptionOfType(InventoryReleaseExceedsCapacityException.class)
                .isThrownBy(() -> inv.release(CLOCK))
                .matches(e -> e.key().equals(inv.key()))
                .matches(e -> e.totalRooms() == 3)
                .matches(e -> e.attemptedAvailableRooms() == 4);
        }

        @Test
        @DisplayName("decrease 직후 release 는 원상 복원")
        void inverse_of_decrease() {
            RoomTypeInventory inv = inventoryWithTotal(5);
            inv.decrease(CLOCK);

            inv.release(CLOCK);

            assertThat(inv.availableRooms().value()).isEqualTo(5);
            assertThat(inv.totalRooms().value()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("restore 불변식")
    class Restore {

        @Test
        @DisplayName("available 이 total 을 초과하면 IllegalArgument")
        void available_exceeds_total() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> RoomTypeInventory.restore(
                    new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE),
                    InventoryCount.of(2),
                    InventoryCount.of(5),
                    0L, NOW, NOW))
                .withMessageContaining("must not exceed");
        }

        @Test
        @DisplayName("적법한 상태 복원")
        void reconstitute_ok() {
            RoomTypeInventory inv = RoomTypeInventory.restore(
                new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE),
                InventoryCount.of(10), InventoryCount.of(7), 3L, NOW, NOW);

            assertThat(inv.totalRooms().value()).isEqualTo(10);
            assertThat(inv.availableRooms().value()).isEqualTo(7);
            assertThat(inv.version()).isEqualTo(3L);
        }
    }

    private static RoomTypeInventory inventoryWithTotal(int total) {
        return RoomTypeInventory.restore(
            new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, STAY_DATE),
            InventoryCount.of(total), InventoryCount.of(total), 0L, NOW, NOW);
    }
}

package com.reservation.hotel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC);

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("create: 초기 상태는 ACTIVE")
        void createStartsActive() {
            Room room = newRoom();
            assertThat(room.status()).isEqualTo(RoomStatus.ACTIVE);
            assertThat(room.createdAt()).isEqualTo(FIXED.instant());
        }
    }

    @Nested
    @DisplayName("Maintenance 전이")
    class Maintenance {

        @Test
        @DisplayName("ACTIVE → UNDER_MAINTENANCE → ACTIVE 왕복")
        void activeAndComplete() {
            Room room = newRoom();
            room.startMaintenance(FIXED);
            assertThat(room.status()).isEqualTo(RoomStatus.UNDER_MAINTENANCE);
            room.completeMaintenance(FIXED);
            assertThat(room.status()).isEqualTo(RoomStatus.ACTIVE);
        }

        @Test
        @DisplayName("UNDER_MAINTENANCE 상태에서 startMaintenance 재호출은 InvalidRoomStateTransitionException")
        void startMaintenanceRejectsDouble() {
            Room room = newRoom();
            room.startMaintenance(FIXED);
            assertThatThrownBy(() -> room.startMaintenance(FIXED))
                .isInstanceOf(com.reservation.hotel.domain.exception.InvalidRoomStateTransitionException.class);
        }

        @Test
        @DisplayName("ACTIVE 상태에서 completeMaintenance 호출은 거부")
        void completeRequiresUnderMaintenance() {
            Room room = newRoom();
            assertThatThrownBy(() -> room.completeMaintenance(FIXED))
                .isInstanceOf(com.reservation.hotel.domain.exception.InvalidRoomStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Soft-delete (deactivate)")
    class Deactivate {

        @Test
        @DisplayName("deactivate: 한 번 호출 시 DEACTIVATED 로 전이")
        void deactivateMovesToDeactivated() {
            Room room = newRoom();
            room.deactivate(FIXED);
            assertThat(room.status()).isEqualTo(RoomStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("deactivate 는 멱등 — 이미 DEACTIVATED 면 updatedAt 도 건드리지 않는다")
        void deactivateIsIdempotent() {
            Clock later = Clock.fixed(FIXED.instant().plusSeconds(60), ZoneOffset.UTC);
            Room room = newRoom();
            room.deactivate(FIXED);
            Instant firstDeactivateAt = room.updatedAt();

            room.deactivate(later);

            assertThat(room.updatedAt()).isEqualTo(firstDeactivateAt);
        }

        @Test
        @DisplayName("DEACTIVATED 에서 reassignRoomType 은 금지")
        void reassignRejectedWhenDeactivated() {
            Room room = newRoom();
            room.deactivate(FIXED);
            RoomTypeId other = RoomTypeId.newId();
            assertThatThrownBy(() -> room.reassignRoomType(other, FIXED))
                .isInstanceOf(com.reservation.hotel.domain.exception.InvalidRoomStateTransitionException.class);
        }

        @Test
        @DisplayName("DEACTIVATED 에서 startMaintenance 는 실패 (InvalidRoomStateTransitionException)")
        void maintenanceRejectedWhenDeactivated() {
            Room room = newRoom();
            room.deactivate(FIXED);
            assertThatThrownBy(() -> room.startMaintenance(FIXED))
                .isInstanceOf(com.reservation.hotel.domain.exception.InvalidRoomStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("reassignRoomType")
    class Reassign {

        @Test
        @DisplayName("새 RoomTypeId 로 교체된다")
        void reassignChangesRoomType() {
            Room room = newRoom();
            RoomTypeId newType = RoomTypeId.newId();

            room.reassignRoomType(newType, FIXED);

            assertThat(room.roomTypeId()).isEqualTo(newType);
        }

        @Test
        @DisplayName("null RoomTypeId 거부")
        void reassignRejectsNull() {
            Room room = newRoom();
            assertThatThrownBy(() -> room.reassignRoomType(null, FIXED))
                .isInstanceOf(NullPointerException.class);
        }
    }

    private static Room newRoom() {
        return Room.create(
            HotelId.newId(),
            RoomTypeId.newId(),
            new Floor(3),
            new RoomNumber("301"),
            FIXED
        );
    }
}

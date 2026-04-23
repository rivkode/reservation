package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoomAssignment Aggregate")
class RoomAssignmentTest {

    private static final RoomId ROOM_ID = RoomId.of("01933333-3333-7aaa-9aaa-111122223333");
    private static final HotelId HOTEL_ID = HotelId.of("01933333-1111-7aaa-9aaa-111122223333");
    private static final RoomTypeId TYPE_A = RoomTypeId.of("01933333-aaaa-7aaa-9aaa-111122223333");
    private static final RoomTypeId TYPE_B = RoomTypeId.of("01933333-bbbb-7aaa-9aaa-111122223333");
    private static final Instant NOW = Instant.parse("2026-04-23T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("초기 매핑 생성")
        void creates_with_initial_mapping() {
            RoomAssignment assignment = RoomAssignment.create(ROOM_ID, HOTEL_ID, TYPE_A, CLOCK);

            assertThat(assignment.roomId()).isEqualTo(ROOM_ID);
            assertThat(assignment.hotelId()).isEqualTo(HOTEL_ID);
            assertThat(assignment.roomTypeId()).isEqualTo(TYPE_A);
            assertThat(assignment.version()).isZero();
            assertThat(assignment.createdAt()).isEqualTo(NOW);
            assertThat(assignment.updatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("reassign")
    class Reassign {

        @Test
        @DisplayName("타입이 바뀌면 이전 타입 반환 + 매핑 갱신 + updatedAt 갱신")
        void changes_type() {
            RoomAssignment assignment = RoomAssignment.create(ROOM_ID, HOTEL_ID, TYPE_A, CLOCK);
            Instant later = NOW.plusSeconds(300);
            Clock laterClock = Clock.fixed(later, ZoneOffset.UTC);

            Optional<RoomTypeId> previous = assignment.reassign(TYPE_B, laterClock);

            assertThat(previous).contains(TYPE_A);
            assertThat(assignment.roomTypeId()).isEqualTo(TYPE_B);
            assertThat(assignment.updatedAt()).isEqualTo(later);
            assertThat(assignment.createdAt()).as("createdAt 불변").isEqualTo(NOW);
        }

        @Test
        @DisplayName("타입이 동일하면 Optional.empty + 상태 불변 — 중복 이벤트 방어")
        void noop_on_same_type() {
            RoomAssignment assignment = RoomAssignment.create(ROOM_ID, HOTEL_ID, TYPE_A, CLOCK);

            Optional<RoomTypeId> previous = assignment.reassign(TYPE_A, CLOCK);

            assertThat(previous).isEmpty();
            assertThat(assignment.roomTypeId()).isEqualTo(TYPE_A);
            assertThat(assignment.updatedAt()).isEqualTo(NOW);
        }
    }
}

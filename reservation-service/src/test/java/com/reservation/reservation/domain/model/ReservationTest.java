package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("Reservation Aggregate")
class ReservationTest {

    private static final HotelId HOTEL = HotelId.of("01933333-1111-7aaa-9aaa-000000000001");
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of("01933333-1111-7aaa-9aaa-000000000002");
    private static final GuestId GUEST = GuestId.of("01933333-1111-7aaa-9aaa-000000000003");
    private static final StayPeriod PERIOD = new StayPeriod(
        LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-03"));
    private static final NumberOfGuests TWO_GUESTS = NumberOfGuests.of(2);
    private static final Instant FIXED_NOW = Instant.parse("2026-04-23T01:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final BillingQuote QUOTE =
        new BillingQuote(Money.of(300_000L, "KRW"), FIXED_NOW);

    @Test
    @DisplayName("create 는 CONFIRMED 상태로 Reservation 을 반환한다 — 이벤트 구성은 Application 책임")
    void createReturnsConfirmedReservation() {
        Reservation reservation = Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, PERIOD, TWO_GUESTS, QUOTE, FIXED_CLOCK);

        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.hotelId()).isEqualTo(HOTEL);
        assertThat(reservation.roomTypeId()).isEqualTo(ROOM_TYPE);
        assertThat(reservation.guestId()).isEqualTo(GUEST);
        assertThat(reservation.stayPeriod()).isEqualTo(PERIOD);
        assertThat(reservation.numberOfGuests()).isEqualTo(TWO_GUESTS);
        assertThat(reservation.quote()).isEqualTo(QUOTE);
        assertThat(reservation.version()).isZero();
        assertThat(reservation.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(reservation.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("create 가 발급한 ReservationId 는 UUID v7 (시간 기반)")
    void reservationIdIsUuidV7() {
        Reservation reservation = Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, PERIOD, TWO_GUESTS, QUOTE, FIXED_CLOCK);

        assertThat(reservation.id().value().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("필수 인자 null 은 NPE")
    void rejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> Reservation.create(
            null, ROOM_TYPE, GUEST, PERIOD, TWO_GUESTS, QUOTE, FIXED_CLOCK));
        assertThatNullPointerException().isThrownBy(() -> Reservation.create(
            HOTEL, null, GUEST, PERIOD, TWO_GUESTS, QUOTE, FIXED_CLOCK));
        assertThatNullPointerException().isThrownBy(() -> Reservation.create(
            HOTEL, ROOM_TYPE, null, PERIOD, TWO_GUESTS, QUOTE, FIXED_CLOCK));
        assertThatNullPointerException().isThrownBy(() -> Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, null, TWO_GUESTS, QUOTE, FIXED_CLOCK));
        assertThatNullPointerException().isThrownBy(() -> Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, PERIOD, null, QUOTE, FIXED_CLOCK));
        assertThatNullPointerException().isThrownBy(() -> Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, PERIOD, TWO_GUESTS, null, FIXED_CLOCK));
        assertThatNullPointerException().isThrownBy(() -> Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, PERIOD, TWO_GUESTS, QUOTE, null));
    }

    @Test
    @DisplayName("restore 는 모든 필드를 그대로 복원한다 — Mapper 가 사용")
    void restoreRebuildsAggregate() {
        ReservationId id = ReservationId.newId();
        Instant createdAt = FIXED_NOW.minusSeconds(60);
        Instant updatedAt = FIXED_NOW;

        Reservation restored = Reservation.restore(
            id, HOTEL, ROOM_TYPE, GUEST, PERIOD, TWO_GUESTS, QUOTE,
            ReservationStatus.CONFIRMED, 7L, createdAt, updatedAt);

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.version()).isEqualTo(7L);
        assertThat(restored.createdAt()).isEqualTo(createdAt);
        assertThat(restored.updatedAt()).isEqualTo(updatedAt);
        assertThat(restored.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }
}

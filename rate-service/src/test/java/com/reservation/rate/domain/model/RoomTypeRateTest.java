package com.reservation.rate.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomTypeRateTest {

    private static final HotelId HOTEL = HotelId.of(UUID.fromString("01970000-0000-7000-8000-000000000001"));
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of(UUID.fromString("01970000-0000-7000-8000-000000000002"));
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);
    private static final Instant T0 = Instant.parse("2026-04-23T10:00:00Z");
    private static final Clock FIXED = Clock.fixed(T0, ZoneOffset.UTC);

    @Test
    @DisplayName("create: 새 RateId 를 발급하고 createdAt · updatedAt 을 Clock 으로 고정")
    void create_신규_등록_시_surrogate_id_발급() {
        RoomTypeRate rate = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(150_000L, "KRW"), FIXED);

        assertThat(rate.id()).isNotNull();
        assertThat(rate.id().value().version()).isEqualTo(7);
        assertThat(rate.hotelId()).isEqualTo(HOTEL);
        assertThat(rate.roomTypeId()).isEqualTo(ROOM_TYPE);
        assertThat(rate.date()).isEqualTo(DATE);
        assertThat(rate.money()).isEqualTo(Money.of(150_000L, "KRW"));
        assertThat(rate.version()).isZero();
        assertThat(rate.createdAt()).isEqualTo(T0);
        assertThat(rate.updatedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("create: null 인자는 모두 즉시 거부")
    void create_null_인자_거부() {
        Money money = Money.of(100L, "KRW");

        assertThatThrownBy(() -> RoomTypeRate.create(null, ROOM_TYPE, DATE, money, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomTypeRate.create(HOTEL, null, DATE, money, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomTypeRate.create(HOTEL, ROOM_TYPE, null, money, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, null, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, money, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("changeAmount: 다른 금액이면 true 반환 + money · updatedAt 갱신")
    void changeAmount_변경_시_true_반환() {
        Instant t1 = T0.plusSeconds(60);
        Clock later = Clock.fixed(t1, ZoneOffset.UTC);
        RoomTypeRate rate = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(150_000L, "KRW"), FIXED);

        boolean changed = rate.changeAmount(Money.of(180_000L, "KRW"), later);

        assertThat(changed).isTrue();
        assertThat(rate.money()).isEqualTo(Money.of(180_000L, "KRW"));
        assertThat(rate.updatedAt()).isEqualTo(t1);
        assertThat(rate.createdAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("changeAmount: 동일 금액이면 false 반환 + 상태 변경 없음")
    void changeAmount_동일_금액이면_noop() {
        Instant t1 = T0.plusSeconds(60);
        Clock later = Clock.fixed(t1, ZoneOffset.UTC);
        RoomTypeRate rate = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(150_000L, "KRW"), FIXED);

        boolean changed = rate.changeAmount(Money.of(150_000L, "KRW"), later);

        assertThat(changed).isFalse();
        assertThat(rate.money()).isEqualTo(Money.of(150_000L, "KRW"));
        assertThat(rate.updatedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("changeAmount: 통화가 다르면 변경으로 간주해 true 반환")
    void changeAmount_통화만_달라도_변경() {
        RoomTypeRate rate = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(100L, "KRW"), FIXED);

        boolean changed = rate.changeAmount(Money.of(100L, "USD"), FIXED);

        assertThat(changed).isTrue();
        assertThat(rate.money().currencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("changeAmount: null 인자 거부")
    void changeAmount_null_거부() {
        RoomTypeRate rate = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(100L, "KRW"), FIXED);

        assertThatThrownBy(() -> rate.changeAmount(null, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> rate.changeAmount(Money.of(200L, "KRW"), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("restore: 영속 상태 그대로 주입, id · version 유지")
    void restore_영속_상태_그대로_복원() {
        RateId id = RateId.newId();
        RoomTypeRate rate = RoomTypeRate.restore(id, HOTEL, ROOM_TYPE, DATE,
            Money.of(200_000L, "KRW"), 3L, T0, T0.plusSeconds(1));

        assertThat(rate.id()).isEqualTo(id);
        assertThat(rate.version()).isEqualTo(3L);
        assertThat(rate.updatedAt()).isEqualTo(T0.plusSeconds(1));
    }
}

package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TwentyFourHourCancellationPolicy")
class TwentyFourHourCancellationPolicyTest {

    private static final HotelId HOTEL = HotelId.of("01933333-1111-7aaa-9aaa-000000000001");
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of("01933333-1111-7aaa-9aaa-000000000002");
    private static final GuestId GUEST = GuestId.of("01933333-1111-7aaa-9aaa-000000000003");
    private static final NumberOfGuests TWO = NumberOfGuests.of(2);
    private static final BillingQuote QUOTE = new BillingQuote(
        Money.of(300_000L, "KRW"), Instant.parse("2026-04-23T01:00:00Z"));
    private static final TwentyFourHourCancellationPolicy POLICY = new TwentyFourHourCancellationPolicy();

    @Test
    @DisplayName("체크인 24시간 초과 이전이면 환불 100%")
    void fullRefundWhenCancelledMoreThan24hBeforeCheckIn() {
        Reservation reservation = reservationWithCheckIn(LocalDate.parse("2026-06-02"));
        // 24h + 1초 (실제로는 25시간 부족) — 명백히 24h 초과 케이스로 분리.
        Instant cancelledAt = Instant.parse("2026-05-31T23:00:00Z");

        CancellationOutcome outcome = POLICY.decide(reservation, CancellationReason.USER_REQUEST, cancelledAt);

        assertThat(outcome.refundRate()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(outcome.policyName()).isEqualTo(TwentyFourHourCancellationPolicy.NAME);
    }

    @Test
    @DisplayName("체크인 24시간 이내이면 환불 0%")
    void noRefundWhenCancelledWithin24h() {
        Reservation reservation = reservationWithCheckIn(LocalDate.parse("2026-06-02"));
        Instant cancelledAt = Instant.parse("2026-06-01T00:00:01Z"); // 24h - 1s

        CancellationOutcome outcome = POLICY.decide(reservation, CancellationReason.USER_REQUEST, cancelledAt);

        assertThat(outcome.refundRate()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("정확히 체크인 24시간 시점에서 취소하면 환불 100% — 경계 (>=) 채택")
    void boundaryExactly24h() {
        Reservation reservation = reservationWithCheckIn(LocalDate.parse("2026-06-02"));
        Instant cancelledAt = Instant.parse("2026-06-01T00:00:00Z"); // 정확히 24h before midnight check-in

        CancellationOutcome outcome = POLICY.decide(reservation, CancellationReason.USER_REQUEST, cancelledAt);

        assertThat(outcome.refundRate()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("BILLING_FAILED 도 동일한 정책 적용 — 본 정책은 reason 무관 (향후 분화 가능)")
    void reasonIgnoredInCurrentPolicy() {
        Reservation reservation = reservationWithCheckIn(LocalDate.parse("2026-06-02"));
        Instant cancelledAt = Instant.parse("2026-06-01T00:00:01Z");

        CancellationOutcome user = POLICY.decide(reservation, CancellationReason.USER_REQUEST, cancelledAt);
        CancellationOutcome saga = POLICY.decide(reservation, CancellationReason.BILLING_FAILED, cancelledAt);

        assertThat(user.refundRate()).isEqualByComparingTo(saga.refundRate());
    }

    private Reservation reservationWithCheckIn(LocalDate checkIn) {
        StayPeriod period = new StayPeriod(checkIn, checkIn.plusDays(1));
        Clock createClock = Clock.fixed(Instant.parse("2026-04-23T01:00:00Z"), ZoneOffset.UTC);
        return Reservation.create(HOTEL, ROOM_TYPE, GUEST, period, TWO, QUOTE, createClock);
    }
}

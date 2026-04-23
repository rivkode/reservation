package com.reservation.reservation.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * PRD §13 Q3 의 초기 2단계 위약금 정책 — 체크인 24시간 전까지 환불 100%, 이내 0%.
 *
 * <p>비교 기준 시각은 체크인 날짜의 자정(KST 가 아닌 UTC) — {@code StayPeriod.checkIn} 은
 * 일자만 들고 있어 시각 정보가 없으므로 {@link LocalTime#MIDNIGHT} 를 적용. 호텔별
 * 체크인 시각 (예: 15:00) 차이를 반영하려면 {@code Hotel} 도메인의 정책을 참조해야
 * 하나, hotel-service 가용성 캐시와의 동기화 문제가 따라오므로 본 PR 에서는 자정 기준
 * 으로 단순화한다 — 결제 PRD 도입 시 정책을 분화 (PRD §13 Q3 "향후 차등 확장").
 *
 * <p>본 클래스는 Spring 어노테이션을 지니지 않는 순수 도메인 객체다. Bean 등록은
 * {@code CancellationPolicyConfig} 가 담당한다 — CLAUDE.md "도메인은 어떤 프레임워크
 * 에도 의존하지 않는다".
 */
public final class TwentyFourHourCancellationPolicy implements CancellationPolicy {

    public static final String NAME = "TWENTY_FOUR_HOUR";

    private static final BigDecimal FULL_REFUND = new BigDecimal("1.00");
    private static final BigDecimal NO_REFUND = new BigDecimal("0.00");
    private static final Duration FREE_WINDOW = Duration.ofHours(24);

    @Override
    public CancellationOutcome decide(Reservation reservation,
                                      CancellationReason reason,
                                      Instant cancelledAt) {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(cancelledAt, "cancelledAt");

        Instant checkInInstant = reservation.stayPeriod().checkIn()
            .atTime(LocalTime.MIDNIGHT)
            .toInstant(ZoneOffset.UTC);
        Duration untilCheckIn = Duration.between(cancelledAt, checkInInstant);
        BigDecimal refundRate = untilCheckIn.compareTo(FREE_WINDOW) >= 0 ? FULL_REFUND : NO_REFUND;
        return new CancellationOutcome(refundRate, NAME);
    }
}

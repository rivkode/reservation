package com.reservation.reservation.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 체크인 ~ 체크아웃 기간 VO.
 *
 * <p>예약 모델에서 {@code stayDate} (재고 차감 단위) 의 집합을 표현한다. 호텔 업계
 * 관습대로 체크아웃 당일은 점유하지 않으므로 재고 차감 대상은 {@code [checkIn, checkOut)}
 * 반개구간이다. {@link #stayDates()} 가 이 의미를 반환해 호출자(Application Service)가
 * for-each 루프로 N일치 inventory 를 차감한다.
 *
 * <p>불변식:
 * <ul>
 *   <li>{@code checkOut} 은 {@code checkIn} 보다 엄격히 큰 날짜여야 한다 (최소 1박)</li>
 *   <li>최대 박수 상한은 본 VO 에서 강제하지 않는다 — 정책 변동 가능성이 커 별도 PR 에서
 *       정책 객체로 분리할 예정 (ddd-architect Q2 결정)</li>
 * </ul>
 */
public record StayPeriod(LocalDate checkIn, LocalDate checkOut) {

    public StayPeriod {
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                "checkOut (" + checkOut + ") must be strictly after checkIn (" + checkIn + ")");
        }
    }

    /** 박수 — N박 = checkOut - checkIn (일). 최소 1. */
    public int nights() {
        return (int) (checkOut.toEpochDay() - checkIn.toEpochDay());
    }

    /** 재고 차감 대상 날짜들 — {@code [checkIn, checkOut)} 반개구간을 오름차순으로. */
    public List<LocalDate> stayDates() {
        return Stream.iterate(checkIn, d -> d.plusDays(1))
            .limit(nights())
            .toList();
    }
}

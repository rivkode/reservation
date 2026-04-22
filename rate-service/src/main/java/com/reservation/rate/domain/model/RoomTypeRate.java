package com.reservation.rate.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * RoomTypeRate Aggregate Root — "특정 호텔 · 객실 타입 · 날짜 한 건" 의 요금 정책.
 *
 * <p>자연키 {@code (hotelId, roomTypeId, date)} 로 식별되는 단일 날짜 단위 AR 이다.
 * 여러 날짜를 묶어 상위 AR (예: RatePlan) 로 구성하지 않는 이유:
 * <ul>
 *   <li>날짜별 변경이 잦아 상위 AR 로 묶으면 낙관적 잠금 충돌 빈도가 폭증한다.</li>
 *   <li>FR-R-02 의 "날짜 범위 조회" 는 Repository 책임이지 AR 경계가 아니다.</li>
 * </ul>
 * Evans §6 — 트랜잭션 일관성 경계는 최소화.
 *
 * <p>불변식:
 * <ul>
 *   <li>{@code hotelId · roomTypeId · date · money} non-null</li>
 *   <li>{@code money.amount >= 0} (Money VO 가 방어)</li>
 *   <li>{@code createdAt ≤ updatedAt}</li>
 * </ul>
 *
 * <p>도메인 행위:
 * <ul>
 *   <li>{@link #create} — 신규 요금 등록. 자연키 충돌 방어는 Application/DB UNIQUE 책임.</li>
 *   <li>{@link #changeAmount} — 금액 변경. 동일 금액이면 {@code false} 를 반환해 no-op
 *       임을 호출자(Application Service) 에 알려 이벤트 발행 여부를 결정하게 한다.</li>
 * </ul>
 */
public class RoomTypeRate {

    private final RateId id;
    private final HotelId hotelId;
    private final RoomTypeId roomTypeId;
    private final LocalDate date;
    private Money money;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private RoomTypeRate(RateId id,
                         HotelId hotelId,
                         RoomTypeId roomTypeId,
                         LocalDate date,
                         Money money,
                         long version,
                         Instant createdAt,
                         Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.hotelId = Objects.requireNonNull(hotelId, "hotelId");
        this.roomTypeId = Objects.requireNonNull(roomTypeId, "roomTypeId");
        this.date = Objects.requireNonNull(date, "date");
        this.money = Objects.requireNonNull(money, "money");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 신규 요금 등록. id 는 내부에서 UUID v7 로 발급한다. */
    public static RoomTypeRate create(HotelId hotelId,
                                      RoomTypeId roomTypeId,
                                      LocalDate date,
                                      Money money,
                                      Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new RoomTypeRate(RateId.newId(), hotelId, roomTypeId, date, money, 0L, now, now);
    }

    /** 영속 저장소에서 복원. 외부(Mapper) 가 보유 중인 상태 그대로 주입. */
    public static RoomTypeRate restore(RateId id,
                                       HotelId hotelId,
                                       RoomTypeId roomTypeId,
                                       LocalDate date,
                                       Money money,
                                       long version,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new RoomTypeRate(id, hotelId, roomTypeId, date, money, version, createdAt, updatedAt);
    }

    /**
     * 금액 변경. 기존 금액과 동일하면 상태를 바꾸지 않고 {@code false} 반환.
     *
     * <p>호출자(Application Service) 는 반환값이 {@code true} 일 때만
     * {@code RoomTypeRateChangedEvent} 를 Outbox 로 발행해야 한다. "동일 값 여부"
     * 는 도메인 지식이므로 Domain 에서 판정하고, 이벤트 발행은 Application 경계
     * (트랜잭션 · Outbox 계약) 에 맡기는 분리.
     *
     * @return 실제로 금액이 변경됐으면 {@code true}, no-op 이면 {@code false}
     */
    public boolean changeAmount(Money newMoney, Clock clock) {
        Objects.requireNonNull(newMoney, "newMoney");
        Objects.requireNonNull(clock, "clock");
        if (this.money.equals(newMoney)) {
            return false;
        }
        this.money = newMoney;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    public RateId id() {
        return id;
    }

    public HotelId hotelId() {
        return hotelId;
    }

    public RoomTypeId roomTypeId() {
        return roomTypeId;
    }

    public LocalDate date() {
        return date;
    }

    public Money money() {
        return money;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

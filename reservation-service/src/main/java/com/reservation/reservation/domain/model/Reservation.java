package com.reservation.reservation.domain.model;

import com.reservation.reservation.domain.exception.ReservationAlreadyCancelledException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 예약 Aggregate Root. {@link ReservationStatus#CONFIRMED} 로 진입해 사용자 취소 또는 Saga
 * 보상 취소를 통해 {@link ReservationStatus#CANCELLED} 로 전이된다.
 *
 * <p>본 Aggregate 는 다음을 SoT 로 보유한다:
 * <ul>
 *   <li>예약 자체의 식별자/상태/대상 (호텔·객실타입·투숙객·기간·인원)</li>
 *   <li>예약 시점에 합의된 청구 견적 ({@link BillingQuote}) — 이후 rate 변경에도 불변</li>
 *   <li>취소가 발생한 경우 {@link Cancellation} (취소 시각·사유·정책 결정 결과)</li>
 * </ul>
 *
 * <p>Aggregate 수준 불변식: {@code (status == CANCELLED) ↔ (cancellation != null)}
 * — 두 필드가 항상 정합되도록 생성/{@link #cancel} 시점에 동시 설정된다 (ddd-architect C1).
 *
 * <p>재고 차감/복원 ({@link RoomTypeInventory#decrease} / {@link RoomTypeInventory#release})
 * 은 Aggregate 외부 책임이다. Application Service 가 같은 로컬 트랜잭션에서 두 Aggregate
 * 를 함께 변경한다 — PRD §3.2 의 SoT 요구사항(오버부킹 방지) 으로 정당화되는 다중
 * Aggregate update 패턴 (ADR 0003).
 *
 * <p>이벤트 발행 위치: Application Service 가 {@code Reservation.create} / {@code cancel}
 * 결과의 getter 만 사용해 contracts 이벤트를 구성한 뒤 {@code OutboxEventPublisher} 로
 * publish 한다. Aggregate 자체가 contracts 를 참조하지 않는다 — ArchUnit 의
 * {@code domainMustNotReferenceContracts} 규칙과 정합.
 */
public class Reservation {

    private final ReservationId id;
    private final HotelId hotelId;
    private final RoomTypeId roomTypeId;
    private final GuestId guestId;
    private final StayPeriod stayPeriod;
    private final NumberOfGuests numberOfGuests;
    private final BillingQuote quote;
    private ReservationStatus status;
    private Cancellation cancellation;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Reservation(ReservationId id,
                        HotelId hotelId,
                        RoomTypeId roomTypeId,
                        GuestId guestId,
                        StayPeriod stayPeriod,
                        NumberOfGuests numberOfGuests,
                        BillingQuote quote,
                        ReservationStatus status,
                        Cancellation cancellation,
                        long version,
                        Instant createdAt,
                        Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.hotelId = Objects.requireNonNull(hotelId, "hotelId");
        this.roomTypeId = Objects.requireNonNull(roomTypeId, "roomTypeId");
        this.guestId = Objects.requireNonNull(guestId, "guestId");
        this.stayPeriod = Objects.requireNonNull(stayPeriod, "stayPeriod");
        this.numberOfGuests = Objects.requireNonNull(numberOfGuests, "numberOfGuests");
        this.quote = Objects.requireNonNull(quote, "quote");
        this.status = Objects.requireNonNull(status, "status");
        this.cancellation = cancellation;
        if ((status == ReservationStatus.CANCELLED) != (cancellation != null)) {
            throw new IllegalArgumentException(
                "Aggregate invariant violated: status=" + status + " but cancellation="
                    + (cancellation == null ? "null" : "present"));
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * 예약을 새로 생성하면서 즉시 CONFIRMED 상태로 확정한다.
     *
     * <p>이벤트 발행은 Application Service 의 책임 — 본 Aggregate 는 contracts 모듈을
     * 참조하지 않는다 (ArchUnit {@code domainMustNotReferenceContracts} 정합).
     */
    public static Reservation create(HotelId hotelId,
                                      RoomTypeId roomTypeId,
                                      GuestId guestId,
                                      StayPeriod stayPeriod,
                                      NumberOfGuests numberOfGuests,
                                      BillingQuote quote,
                                      Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new Reservation(
            ReservationId.newId(),
            hotelId, roomTypeId, guestId,
            stayPeriod, numberOfGuests, quote,
            ReservationStatus.CONFIRMED,
            null,
            0L,
            now,
            now
        );
    }

    /** 영속 저장소에서 복원 — Mapper 가 보유 중인 상태 그대로 주입. */
    public static Reservation restore(ReservationId id,
                                       HotelId hotelId,
                                       RoomTypeId roomTypeId,
                                       GuestId guestId,
                                       StayPeriod stayPeriod,
                                       NumberOfGuests numberOfGuests,
                                       BillingQuote quote,
                                       ReservationStatus status,
                                       Cancellation cancellation,
                                       long version,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Reservation(id, hotelId, roomTypeId, guestId, stayPeriod, numberOfGuests,
            quote, status, cancellation, version, createdAt, updatedAt);
    }

    /**
     * 예약을 CANCELLED 상태로 전이한다.
     *
     * <p>이미 CANCELLED 면 {@link ReservationAlreadyCancelledException} — Application
     * Service 가 사용자 경로에선 그대로 전파(409), Saga 보상 경로에선 catch 후 noop 처리.
     *
     * <p>{@code policy.decide} 결과는 {@link Cancellation} 에 함께 보존되어 향후 결제 PRD
     * 도입 시 어떤 정책으로 환불률이 결정됐는지 감사할 수 있게 한다 (ddd-architect C1
     * — refundRate dead-data 의미 약화).
     *
     * <p>재고 복원은 본 Aggregate 의 책임이 아니다. Application Service 가 같은 로컬
     * 트랜잭션에서 N 일치 {@link RoomTypeInventory#release} 를 함께 호출한다 (ADR 0003
     * SoT 요구사항 — 사용자/보상 경로 모두 동일 정당화, ddd-architect L2).
     */
    public void cancel(CancellationPolicy policy, CancellationReason reason, Clock clock) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(clock, "clock");
        if (status == ReservationStatus.CANCELLED) {
            throw new ReservationAlreadyCancelledException(id);
        }
        Instant now = Instant.now(clock);
        CancellationOutcome outcome = policy.decide(this, reason, now);
        this.cancellation = new Cancellation(now, reason, outcome);
        this.status = ReservationStatus.CANCELLED;
        this.updatedAt = now;
    }

    public ReservationId id() {
        return id;
    }

    public HotelId hotelId() {
        return hotelId;
    }

    public RoomTypeId roomTypeId() {
        return roomTypeId;
    }

    public GuestId guestId() {
        return guestId;
    }

    public StayPeriod stayPeriod() {
        return stayPeriod;
    }

    public NumberOfGuests numberOfGuests() {
        return numberOfGuests;
    }

    public BillingQuote quote() {
        return quote;
    }

    public ReservationStatus status() {
        return status;
    }

    public Optional<Cancellation> cancellation() {
        return Optional.ofNullable(cancellation);
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

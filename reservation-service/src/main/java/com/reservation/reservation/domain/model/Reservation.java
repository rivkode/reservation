package com.reservation.reservation.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 예약 Aggregate Root. PR-2.2 범위에서는 생성 즉시 {@link ReservationStatus#CONFIRMED}
 * 로 확정되는 단일 진입 상태만 다룬다 (취소 전이는 PR-2.3).
 *
 * <p>본 Aggregate 는 다음을 SoT 로 보유한다:
 * <ul>
 *   <li>예약 자체의 식별자/상태/대상 (호텔·객실타입·투숙객·기간·인원)</li>
 *   <li>예약 시점에 합의된 청구 견적 ({@link BillingQuote}) — 이후 rate 변경에도 불변</li>
 * </ul>
 *
 * <p>재고 차감 ({@link RoomTypeInventory#decrease}) 은 Aggregate 외부 책임이다.
 * Application Service 가 같은 로컬 트랜잭션에서 두 Aggregate 를 함께 변경한다 — PRD §3.2
 * 의 SoT 요구사항(오버부킹 방지) 으로 정당화되는 다중 Aggregate update 패턴.
 *
 * <p>이벤트 발행 위치: Application Service 가 {@code Reservation.create} 결과의 getter
 * 만 사용해 {@code ReservationCreatedEvent} 를 구성한 뒤 {@code OutboxEventPublisher}
 * 로 publish 한다. Aggregate 자체가 contracts 를 참조하지 않는다 — ArchUnit 의
 * {@code domainMustNotReferenceContracts} 규칙과 정합. 이벤트의 "내용 결정권" 은
 * Aggregate 의 게터(불변 필드) 에 캡슐화되어 있으므로 Application 은 단순 매핑만 수행.
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
                                       long version,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Reservation(id, hotelId, roomTypeId, guestId, stayPeriod, numberOfGuests,
            quote, status, version, createdAt, updatedAt);
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

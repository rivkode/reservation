package com.reservation.reservation.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.reservation.domain.model.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code reservation} 테이블 매핑. PR-2.2 범위는 생성/조회 단건. 상태 전이(취소) 와
 * 부가 인덱스(투숙객/호텔별 조회) 는 PR-2.3 / PR-2.4 에서 확장한다.
 *
 * <p>{@code total_amount} · {@code currency} 는 {@code BillingQuote} VO 의 직렬화이며,
 * 이후 rate 변경에도 본 예약의 청구액은 본 컬럼으로 고정된다 — ddd-architect H1.
 */
@Entity
@Table(name = "reservation")
public class ReservationJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID id;

    @Column(name = "hotel_id", nullable = false, columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID hotelId;

    @Column(name = "room_type_id", nullable = false, columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID roomTypeId;

    @Column(name = "guest_id", nullable = false, columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID guestId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "number_of_guests", nullable = false)
    private int numberOfGuests;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "quoted_at", nullable = false)
    private Instant quotedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReservationStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReservationJpaEntity() {
    }

    public ReservationJpaEntity(UUID id, UUID hotelId, UUID roomTypeId, UUID guestId,
                                 LocalDate checkInDate, LocalDate checkOutDate,
                                 int numberOfGuests,
                                 long totalAmount, String currency, Instant quotedAt,
                                 ReservationStatus status, long version,
                                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.hotelId = hotelId;
        this.roomTypeId = roomTypeId;
        this.guestId = guestId;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.numberOfGuests = numberOfGuests;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.quotedAt = quotedAt;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public UUID getRoomTypeId() {
        return roomTypeId;
    }

    public UUID getGuestId() {
        return guestId;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public int getNumberOfGuests() {
        return numberOfGuests;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getQuotedAt() {
        return quotedAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

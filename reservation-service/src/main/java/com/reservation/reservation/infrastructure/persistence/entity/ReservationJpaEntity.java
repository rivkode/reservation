package com.reservation.reservation.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.reservation.domain.model.CancellationReason;
import com.reservation.reservation.domain.model.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code reservation} 테이블 매핑.
 *
 * <p>{@code total_amount} · {@code currency} · {@code quoted_at} 은 {@code BillingQuote} VO
 * 의 직렬화 — rate 변경에도 본 예약의 청구액은 고정 (ddd-architect H1).
 *
 * <p>{@code cancelled_at} · {@code cancellation_reason} · {@code refund_rate}
 * · {@code cancellation_policy_name} 4 컬럼은 {@code Cancellation} VO 의 평탄화 매핑이며
 * {@code status == CANCELLED} 일 때만 채워진다 (Aggregate 불변식, ddd-architect C1).
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

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 32)
    private CancellationReason cancellationReason;

    @Column(name = "refund_rate", precision = 3, scale = 2)
    private BigDecimal refundRate;

    @Column(name = "cancellation_policy_name", length = 64)
    private String cancellationPolicyName;

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
                                 ReservationStatus status,
                                 Instant cancelledAt, CancellationReason cancellationReason,
                                 BigDecimal refundRate, String cancellationPolicyName,
                                 long version, Instant createdAt, Instant updatedAt) {
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
        this.cancelledAt = cancelledAt;
        this.cancellationReason = cancellationReason;
        this.refundRate = refundRate;
        this.cancellationPolicyName = cancellationPolicyName;
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

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public BigDecimal getRefundRate() {
        return refundRate;
    }

    public String getCancellationPolicyName() {
        return cancellationPolicyName;
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

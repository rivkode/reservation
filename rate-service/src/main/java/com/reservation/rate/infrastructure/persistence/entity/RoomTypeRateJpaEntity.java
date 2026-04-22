package com.reservation.rate.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code room_type_rate} 테이블 매핑. id 는 surrogate (BINARY(16) UUIDv7),
 * 자연키 {@code (hotel_id, room_type_id, rate_date)} 는 DB UNIQUE 로 보호한다.
 * 금액은 통화 최소 단위 BIGINT, 통화 코드는 VARCHAR(3) ISO-4217.
 */
@Entity
@Table(name = "room_type_rate")
public class RoomTypeRateJpaEntity {

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

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomTypeRateJpaEntity() {
    }

    public RoomTypeRateJpaEntity(UUID id, UUID hotelId, UUID roomTypeId, LocalDate rateDate,
                                 long amount, String currency,
                                 long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.hotelId = hotelId;
        this.roomTypeId = roomTypeId;
        this.rateDate = rateDate;
        this.amount = amount;
        this.currency = currency;
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

    public LocalDate getRateDate() {
        return rateDate;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
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

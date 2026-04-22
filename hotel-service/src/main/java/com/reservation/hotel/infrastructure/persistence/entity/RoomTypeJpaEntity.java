package com.reservation.hotel.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_type")
public class RoomTypeJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID id;

    @Column(name = "hotel_id", nullable = false, columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID hotelId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomTypeJpaEntity() {
    }

    public RoomTypeJpaEntity(UUID id, UUID hotelId, String name, int maxOccupancy,
                             long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.hotelId = hotelId;
        this.name = name;
        this.maxOccupancy = maxOccupancy;
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

    public String getName() {
        return name;
    }

    public int getMaxOccupancy() {
        return maxOccupancy;
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

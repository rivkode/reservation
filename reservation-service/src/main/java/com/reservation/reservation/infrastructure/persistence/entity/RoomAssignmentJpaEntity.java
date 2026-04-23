package com.reservation.reservation.infrastructure.persistence.entity;

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
@Table(name = "room_assignment")
public class RoomAssignmentJpaEntity {

    @Id
    @Column(name = "room_id", columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID roomId;

    @Column(name = "hotel_id", columnDefinition = "BINARY(16)", nullable = false)
    @Convert(converter = UuidBinaryConverter.class)
    private UUID hotelId;

    @Column(name = "room_type_id", columnDefinition = "BINARY(16)", nullable = false)
    @Convert(converter = UuidBinaryConverter.class)
    private UUID roomTypeId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomAssignmentJpaEntity() {
    }

    public RoomAssignmentJpaEntity(UUID roomId,
                                    UUID hotelId,
                                    UUID roomTypeId,
                                    long version,
                                    Instant createdAt,
                                    Instant updatedAt) {
        this.roomId = roomId;
        this.hotelId = hotelId;
        this.roomTypeId = roomTypeId;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public UUID getRoomTypeId() {
        return roomTypeId;
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

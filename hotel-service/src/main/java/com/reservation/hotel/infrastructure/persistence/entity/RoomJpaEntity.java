package com.reservation.hotel.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.hotel.domain.model.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room")
public class RoomJpaEntity {

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

    @Column(name = "floor_no", nullable = false)
    private int floor;

    @Column(name = "room_number", nullable = false, length = 16)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RoomStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomJpaEntity() {
    }

    public RoomJpaEntity(UUID id, UUID hotelId, UUID roomTypeId,
                         int floor, String number, RoomStatus status,
                         long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.hotelId = hotelId;
        this.roomTypeId = roomTypeId;
        this.floor = floor;
        this.number = number;
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

    public int getFloor() {
        return floor;
    }

    public String getNumber() {
        return number;
    }

    public RoomStatus getStatus() {
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

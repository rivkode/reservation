package com.reservation.reservation.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code room_type_inventory} 테이블 매핑. 자연 복합 키 {@code (hotelId, roomTypeId, stayDate)}
 * 를 {@link InventoryPk} 로 감싸 Repository 조회 API 를 단순하게 유지한다.
 */
@Entity
@Table(name = "room_type_inventory")
public class RoomTypeInventoryJpaEntity {

    @EmbeddedId
    private InventoryPk id;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    @Column(name = "available_rooms", nullable = false)
    private int availableRooms;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomTypeInventoryJpaEntity() {
    }

    public RoomTypeInventoryJpaEntity(InventoryPk id,
                                       int totalRooms,
                                       int availableRooms,
                                       long version,
                                       Instant createdAt,
                                       Instant updatedAt) {
        this.id = id;
        this.totalRooms = totalRooms;
        this.availableRooms = availableRooms;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public InventoryPk getId() {
        return id;
    }

    public int getTotalRooms() {
        return totalRooms;
    }

    public int getAvailableRooms() {
        return availableRooms;
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

    @Embeddable
    public static class InventoryPk implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "hotel_id", columnDefinition = "BINARY(16)")
        @Convert(converter = UuidBinaryConverter.class)
        private UUID hotelId;

        @Column(name = "room_type_id", columnDefinition = "BINARY(16)")
        @Convert(converter = UuidBinaryConverter.class)
        private UUID roomTypeId;

        @Column(name = "stay_date")
        private LocalDate stayDate;

        protected InventoryPk() {
        }

        public InventoryPk(UUID hotelId, UUID roomTypeId, LocalDate stayDate) {
            this.hotelId = hotelId;
            this.roomTypeId = roomTypeId;
            this.stayDate = stayDate;
        }

        public UUID getHotelId() {
            return hotelId;
        }

        public UUID getRoomTypeId() {
            return roomTypeId;
        }

        public LocalDate getStayDate() {
            return stayDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InventoryPk other)) return false;
            return Objects.equals(hotelId, other.hotelId)
                && Objects.equals(roomTypeId, other.roomTypeId)
                && Objects.equals(stayDate, other.stayDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hotelId, roomTypeId, stayDate);
        }
    }
}

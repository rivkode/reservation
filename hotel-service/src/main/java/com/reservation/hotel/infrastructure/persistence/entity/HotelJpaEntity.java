package com.reservation.hotel.infrastructure.persistence.entity;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.hotel.domain.model.Amenity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "hotel")
public class HotelJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    @Convert(converter = UuidBinaryConverter.class)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address_street", nullable = false, length = 200)
    private String addressStreet;

    @Column(name = "address_city", nullable = false, length = 100)
    private String addressCity;

    @Column(name = "address_country", nullable = false, length = 100)
    private String addressCountry;

    @Column(name = "star_rating", nullable = false)
    private int starRating;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = Amenity.class)
    @CollectionTable(
        name = "hotel_amenity",
        joinColumns = @JoinColumn(name = "hotel_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_hotel_amenity_hotel"))
    )
    @Column(name = "amenity", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<Amenity> amenities = new HashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HotelJpaEntity() {
    }

    public HotelJpaEntity(UUID id, String name,
                          String addressStreet, String addressCity, String addressCountry,
                          int starRating, Set<Amenity> amenities, long version,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.addressStreet = addressStreet;
        this.addressCity = addressCity;
        this.addressCountry = addressCountry;
        this.starRating = starRating;
        this.amenities = amenities == null || amenities.isEmpty()
            ? EnumSet.noneOf(Amenity.class) : EnumSet.copyOf(amenities);
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddressStreet() {
        return addressStreet;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public int getStarRating() {
        return starRating;
    }

    public Set<Amenity> getAmenities() {
        return amenities;
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

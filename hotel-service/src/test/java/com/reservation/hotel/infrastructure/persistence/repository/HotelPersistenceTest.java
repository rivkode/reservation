package com.reservation.hotel.infrastructure.persistence.repository;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.hotel.domain.model.Amenity;
import com.reservation.hotel.domain.model.Floor;
import com.reservation.hotel.domain.model.Hotel;
import com.reservation.hotel.domain.model.HotelAddress;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.HotelName;
import com.reservation.hotel.domain.model.MaxOccupancy;
import com.reservation.hotel.domain.model.Room;
import com.reservation.hotel.domain.model.RoomNumber;
import com.reservation.hotel.domain.model.RoomType;
import com.reservation.hotel.domain.model.RoomTypeName;
import com.reservation.hotel.domain.model.StarRating;
import com.reservation.hotel.infrastructure.persistence.entity.HotelJpaEntity;
import com.reservation.hotel.infrastructure.persistence.entity.RoomTypeJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * hotel-service JPA 매핑 + Flyway V1 스키마 조합을 MySQL 컨테이너 위에서 검증한다.
 *
 * <p>@TestPropertySource 로 MySQL 전용 설정을 주입해 기본 application-test.yml 의
 * H2 in-memory 설정을 이 클래스에 한해 덮어쓴다. MysqlContainerExtension 이 system
 * property 로 datasource 를 주입하지만 일부 키는 application-test.yml 가 선점하므로
 * 본 선언으로 명시적 우선순위를 부여한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UuidBinaryConverter.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@ExtendWith(MysqlContainerExtension.class)
class HotelPersistenceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-22T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    HotelJpaRepository hotelJpaRepository;
    @Autowired
    RoomTypeJpaRepository roomTypeJpaRepository;
    @Autowired
    RoomJpaRepository roomJpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("Hotel save → findById: amenities ElementCollection 왕복 보존")
    void hotelRoundTrip() {
        Hotel hotel = Hotel.create(
            new HotelName("H"),
            new HotelAddress("1 St", "Seoul", "KR"),
            new StarRating(4),
            EnumSet.of(Amenity.WIFI, Amenity.POOL),
            FIXED
        );
        HotelRepositoryImpl repo = new HotelRepositoryImpl(hotelJpaRepository);

        repo.save(hotel);
        em.flush();
        em.clear();

        Hotel loaded = repo.findById(hotel.id()).orElseThrow();
        assertThat(loaded.name().value()).isEqualTo("H");
        assertThat(loaded.address().city()).isEqualTo("Seoul");
        assertThat(loaded.amenities()).containsExactlyInAnyOrder(Amenity.WIFI, Amenity.POOL);
    }

    @Test
    @DisplayName("RoomType UNIQUE(hotelId,name) 는 existsByHotelIdAndName 으로 확인 가능")
    void roomTypeUniqueCheck() {
        HotelId hotelId = saveHotel();
        RoomTypeRepositoryImpl repo = new RoomTypeRepositoryImpl(roomTypeJpaRepository);

        repo.save(RoomType.create(hotelId, new RoomTypeName("Standard"), new MaxOccupancy(2), FIXED));
        em.flush();
        em.clear();

        assertThat(repo.existsByHotelIdAndName(hotelId, new RoomTypeName("Standard"))).isTrue();
        assertThat(repo.existsByHotelIdAndName(hotelId, new RoomTypeName("Deluxe"))).isFalse();
    }

    @Test
    @DisplayName("Room: existsByHotelIdAndFloorAndNumber 가 같은 컬럼 매핑을 사용하는지 확인")
    void roomUniqueCheck() {
        HotelId hotelId = saveHotel();
        RoomType roomType = RoomType.create(hotelId, new RoomTypeName("Standard"), new MaxOccupancy(2), FIXED);
        roomTypeJpaRepository.save(toRoomTypeEntity(roomType));
        RoomRepositoryImpl repo = new RoomRepositoryImpl(roomJpaRepository);

        repo.save(Room.create(hotelId, roomType.id(), new Floor(3), new RoomNumber("301"), FIXED));
        em.flush();
        em.clear();

        assertThat(repo.existsByHotelIdAndFloorAndNumber(hotelId, new Floor(3), new RoomNumber("301"))).isTrue();
        assertThat(repo.existsByHotelIdAndFloorAndNumber(hotelId, new Floor(3), new RoomNumber("302"))).isFalse();
    }

    @Test
    @DisplayName("findByHotelId 로 다건 조회")
    void findByHotelIdReturnsAll() {
        HotelId hotelId = saveHotel();
        RoomTypeRepositoryImpl repo = new RoomTypeRepositoryImpl(roomTypeJpaRepository);
        repo.save(RoomType.create(hotelId, new RoomTypeName("Standard"), new MaxOccupancy(2), FIXED));
        repo.save(RoomType.create(hotelId, new RoomTypeName("Deluxe"), new MaxOccupancy(3), FIXED));
        em.flush();
        em.clear();

        List<RoomType> results = repo.findByHotelId(hotelId);

        assertThat(results).hasSize(2);
    }

    private HotelId saveHotel() {
        Hotel hotel = Hotel.create(
            new HotelName("H"),
            new HotelAddress("1 St", "Seoul", "KR"),
            new StarRating(4),
            EnumSet.noneOf(Amenity.class),
            FIXED
        );
        hotelJpaRepository.save(new HotelJpaEntity(
            hotel.id().value(),
            hotel.name().value(),
            hotel.address().street(), hotel.address().city(), hotel.address().country(),
            hotel.starRating().value(),
            hotel.amenities(),
            0L,
            hotel.createdAt(), hotel.updatedAt()
        ));
        return hotel.id();
    }

    private RoomTypeJpaEntity toRoomTypeEntity(RoomType rt) {
        return new RoomTypeJpaEntity(rt.id().value(), rt.hotelId().value(),
            rt.name().value(), rt.maxOccupancy().value(),
            0L, rt.createdAt(), rt.updatedAt());
    }
}

package com.reservation.rate.infrastructure.persistence.repository;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.Money;
import com.reservation.rate.domain.model.RoomTypeId;
import com.reservation.rate.domain.model.RoomTypeRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * rate-service JPA 매핑 + Flyway V1 스키마 조합을 MySQL 컨테이너 위에서 검증한다.
 * hotel-service 의 HotelPersistenceTest 와 동일 슬라이스 전략.
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
class RoomTypeRatePersistenceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
    private static final HotelId HOTEL = HotelId.of(UUID.fromString("01970000-0000-7000-8000-000000000001"));
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of(UUID.fromString("01970000-0000-7000-8000-000000000002"));

    @Autowired
    RoomTypeRateJpaRepository jpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("save → findById: 속성 모두 왕복 보존")
    void roundTrip() {
        RoomTypeRateRepositoryImpl repo = new RoomTypeRateRepositoryImpl(jpaRepository);
        RoomTypeRate rate = RoomTypeRate.create(HOTEL, ROOM_TYPE,
            LocalDate.of(2026, 6, 1), Money.of(150_000L, "KRW"), FIXED);

        repo.save(rate);
        em.flush();
        em.clear();

        RoomTypeRate loaded = repo.findById(rate.id()).orElseThrow();
        assertThat(loaded.hotelId()).isEqualTo(HOTEL);
        assertThat(loaded.roomTypeId()).isEqualTo(ROOM_TYPE);
        assertThat(loaded.date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(loaded.money()).isEqualTo(Money.of(150_000L, "KRW"));
    }

    @Test
    @DisplayName("existsByNaturalKey: (hotelId, roomTypeId, date) 세 필드 모두 필터링 대상")
    void existsByNaturalKey() {
        RoomTypeRateRepositoryImpl repo = new RoomTypeRateRepositoryImpl(jpaRepository);
        LocalDate date = LocalDate.of(2026, 6, 1);
        HotelId otherHotel = HotelId.of(UUID.fromString("01970000-0000-7000-8000-000000000099"));
        RoomTypeId otherRoomType = RoomTypeId.of(UUID.fromString("01970000-0000-7000-8000-0000000000aa"));
        repo.save(RoomTypeRate.create(HOTEL, ROOM_TYPE, date, Money.of(150_000L, "KRW"), FIXED));
        em.flush();
        em.clear();

        assertThat(repo.existsByNaturalKey(HOTEL, ROOM_TYPE, date)).isTrue();
        // 세 필드 각각을 달리해도 false — 자연키 조회 계약 검증
        assertThat(repo.existsByNaturalKey(HOTEL, ROOM_TYPE, date.plusDays(1))).isFalse();
        assertThat(repo.existsByNaturalKey(otherHotel, ROOM_TYPE, date)).isFalse();
        assertThat(repo.existsByNaturalKey(HOTEL, otherRoomType, date)).isFalse();
    }

    @Test
    @DisplayName("자연키 UNIQUE(hotel_id, room_type_id, rate_date) 위반은 ConstraintViolationException")
    void naturalKeyUniqueConstraint() {
        RoomTypeRateRepositoryImpl repo = new RoomTypeRateRepositoryImpl(jpaRepository);
        LocalDate date = LocalDate.of(2026, 6, 1);
        repo.save(RoomTypeRate.create(HOTEL, ROOM_TYPE, date, Money.of(150_000L, "KRW"), FIXED));
        em.flush();

        // 다른 surrogate id 지만 동일 자연키 → UNIQUE 위반.
        // em.flush() 가 직접 JPA 를 거치므로 Spring 의 @Repository 예외 변환이 적용되지
        // 않고 Hibernate 의 ConstraintViolationException 이 그대로 올라온다.
        RoomTypeRate dup = RoomTypeRate.create(HOTEL, ROOM_TYPE, date, Money.of(180_000L, "KRW"), FIXED);
        assertThatThrownBy(() -> {
            repo.save(dup);
            em.flush();
        }).isInstanceOf(ConstraintViolationException.class)
          .hasMessageContaining("uk_room_type_rate_natural");
    }

    @Test
    @DisplayName("findByRange: [from, to] 구간을 rate_date ASC 순서로 반환")
    void findByRangeOrdersByDateAsc() {
        RoomTypeRateRepositoryImpl repo = new RoomTypeRateRepositoryImpl(jpaRepository);
        LocalDate d1 = LocalDate.of(2026, 6, 1);
        LocalDate d2 = LocalDate.of(2026, 6, 2);
        LocalDate d3 = LocalDate.of(2026, 6, 3);
        repo.save(RoomTypeRate.create(HOTEL, ROOM_TYPE, d2, Money.of(170_000L, "KRW"), FIXED));
        repo.save(RoomTypeRate.create(HOTEL, ROOM_TYPE, d1, Money.of(150_000L, "KRW"), FIXED));
        repo.save(RoomTypeRate.create(HOTEL, ROOM_TYPE, d3, Money.of(180_000L, "KRW"), FIXED));
        em.flush();
        em.clear();

        List<RoomTypeRate> results = repo.findByRange(HOTEL, ROOM_TYPE, d1, d3);

        assertThat(results).hasSize(3)
            .extracting(RoomTypeRate::date)
            .containsExactly(d1, d2, d3);
    }

    @Test
    @DisplayName("findByRange: 다른 호텔 · 객실 타입은 필터링")
    void findByRangeFiltersByNaturalKey() {
        RoomTypeRateRepositoryImpl repo = new RoomTypeRateRepositoryImpl(jpaRepository);
        LocalDate date = LocalDate.of(2026, 6, 1);
        HotelId otherHotel = HotelId.of(UUID.fromString("01970000-0000-7000-8000-000000000099"));
        repo.save(RoomTypeRate.create(HOTEL, ROOM_TYPE, date, Money.of(150_000L, "KRW"), FIXED));
        repo.save(RoomTypeRate.create(otherHotel, ROOM_TYPE, date, Money.of(999_000L, "KRW"), FIXED));
        em.flush();
        em.clear();

        List<RoomTypeRate> results = repo.findByRange(HOTEL, ROOM_TYPE, date, date);

        assertThat(results).hasSize(1)
            .extracting(r -> r.money().amount())
            .containsExactly(150_000L);
    }
}

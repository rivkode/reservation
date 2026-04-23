package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.reservation.domain.model.BillingQuote;
import com.reservation.reservation.domain.model.CancellationReason;
import com.reservation.reservation.domain.model.GuestId;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.NumberOfGuests;
import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationId;
import com.reservation.reservation.domain.model.ReservationStatus;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.StayPeriod;
import com.reservation.reservation.domain.model.TwentyFourHourCancellationPolicy;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
class ReservationPersistenceTest {

    private static final HotelId HOTEL = HotelId.of("01933333-1111-7aaa-9aaa-000000000001");
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of("01933333-1111-7aaa-9aaa-000000000002");
    private static final GuestId GUEST = GuestId.of("01933333-1111-7aaa-9aaa-000000000003");
    private static final StayPeriod PERIOD = new StayPeriod(
        LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-03"));
    private static final Instant NOW = Instant.parse("2026-04-23T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired
    ReservationJpaRepository jpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("save 후 findById 로 모든 필드를 round-trip — 도메인 ↔ JPA 매핑 정합")
    void roundTrip() {
        ReservationRepositoryImpl repository = new ReservationRepositoryImpl(jpaRepository);
        Reservation created = Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, PERIOD, NumberOfGuests.of(2),
            new BillingQuote(Money.of(300_000L, "KRW"), NOW), CLOCK);
        Reservation saved = repository.save(created);
        em.flush();
        em.clear();

        Optional<Reservation> loaded = repository.findById(saved.id());

        assertThat(loaded).isPresent();
        Reservation reservation = loaded.get();
        assertThat(reservation.id()).isEqualTo(saved.id());
        assertThat(reservation.hotelId()).isEqualTo(HOTEL);
        assertThat(reservation.roomTypeId()).isEqualTo(ROOM_TYPE);
        assertThat(reservation.guestId()).isEqualTo(GUEST);
        assertThat(reservation.stayPeriod()).isEqualTo(PERIOD);
        assertThat(reservation.numberOfGuests().value()).isEqualTo(2);
        assertThat(reservation.quote().total()).isEqualTo(Money.of(300_000L, "KRW"));
        assertThat(reservation.quote().quotedAt()).isEqualTo(NOW);
        assertThat(reservation.status()).isEqualTo(saved.status());
        assertThat(reservation.createdAt()).isEqualTo(NOW);
        assertThat(reservation.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("존재하지 않는 ReservationId 조회는 Optional.empty")
    void findByIdMissing() {
        ReservationRepositoryImpl repository = new ReservationRepositoryImpl(jpaRepository);

        Optional<Reservation> result = repository.findById(ReservationId.newId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("CANCELLED 예약 round-trip — Cancellation VO 4 컬럼 평탄화 ↔ 복원 정합")
    void cancellationRoundTrip() {
        ReservationRepositoryImpl repository = new ReservationRepositoryImpl(jpaRepository);
        Reservation reservation = Reservation.create(
            HOTEL, ROOM_TYPE, GUEST, PERIOD, NumberOfGuests.of(2),
            new BillingQuote(Money.of(300_000L, "KRW"), NOW), CLOCK);
        Instant cancelAt = Instant.parse("2026-05-15T03:00:00Z"); // 24h 전이라 환불 100%
        Clock cancelClock = Clock.fixed(cancelAt, ZoneOffset.UTC);
        reservation.cancel(new TwentyFourHourCancellationPolicy(),
            CancellationReason.USER_REQUEST, cancelClock);
        Reservation saved = repository.save(reservation);
        em.flush();
        em.clear();

        Optional<Reservation> loaded = repository.findById(saved.id());

        assertThat(loaded).isPresent();
        Reservation r = loaded.get();
        assertThat(r.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(r.cancellation()).isPresent();
        assertThat(r.cancellation().orElseThrow().cancelledAt()).isEqualTo(cancelAt);
        assertThat(r.cancellation().orElseThrow().reason())
            .isEqualTo(CancellationReason.USER_REQUEST);
        assertThat(r.cancellation().orElseThrow().outcome().refundRate())
            .isEqualByComparingTo(new java.math.BigDecimal("1.00"));
        assertThat(r.cancellation().orElseThrow().outcome().policyName())
            .isEqualTo(TwentyFourHourCancellationPolicy.NAME);
    }
}

package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.RoomAssignment;
import com.reservation.reservation.domain.model.RoomId;
import com.reservation.reservation.domain.model.RoomTypeId;
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
class RoomAssignmentPersistenceTest {

    private static final RoomId ROOM_ID = RoomId.of("01933333-3333-7aaa-9aaa-111122223333");
    private static final HotelId HOTEL_ID = HotelId.of("01933333-1111-7aaa-9aaa-111122223333");
    private static final RoomTypeId TYPE_A = RoomTypeId.of("01933333-aaaa-7aaa-9aaa-111122223333");
    private static final RoomTypeId TYPE_B = RoomTypeId.of("01933333-bbbb-7aaa-9aaa-111122223333");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    RoomAssignmentJpaRepository jpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("save → findByRoomId: 필드 왕복 보존")
    void roundTrip() {
        RoomAssignmentRepositoryImpl repo = new RoomAssignmentRepositoryImpl(jpaRepository);

        repo.save(RoomAssignment.create(ROOM_ID, HOTEL_ID, TYPE_A, FIXED));
        em.flush();
        em.clear();

        RoomAssignment loaded = repo.findByRoomId(ROOM_ID).orElseThrow();
        assertThat(loaded.roomTypeId()).isEqualTo(TYPE_A);
        assertThat(loaded.hotelId()).isEqualTo(HOTEL_ID);
    }

    @Test
    @DisplayName("reassign → save 왕복: 새 타입이 영속됨")
    void reassignPersists() {
        RoomAssignmentRepositoryImpl repo = new RoomAssignmentRepositoryImpl(jpaRepository);
        RoomAssignment original = RoomAssignment.create(ROOM_ID, HOTEL_ID, TYPE_A, FIXED);
        repo.save(original);
        em.flush();
        em.clear();

        RoomAssignment loaded = repo.findByRoomId(ROOM_ID).orElseThrow();
        loaded.reassign(TYPE_B, FIXED);
        repo.save(loaded);
        em.flush();
        em.clear();

        assertThat(repo.findByRoomId(ROOM_ID).orElseThrow().roomTypeId()).isEqualTo(TYPE_B);
    }

    @Test
    @DisplayName("deleteByRoomId: 존재하던 매핑 제거")
    void deleteRemovesRow() {
        RoomAssignmentRepositoryImpl repo = new RoomAssignmentRepositoryImpl(jpaRepository);
        repo.save(RoomAssignment.create(ROOM_ID, HOTEL_ID, TYPE_A, FIXED));
        em.flush();

        repo.deleteByRoomId(ROOM_ID);
        em.flush();
        em.clear();

        assertThat(repo.findByRoomId(ROOM_ID)).isEmpty();
    }
}

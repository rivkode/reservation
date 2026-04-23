package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryCount;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import org.hibernate.exception.GenericJDBCException;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class RoomTypeInventoryPersistenceTest {

    private static final HotelId HOTEL_ID = HotelId.of("01933333-1111-7aaa-9aaa-111122223333");
    private static final RoomTypeId ROOM_TYPE_ID = RoomTypeId.of("01933333-aaaa-7aaa-9aaa-111122223333");
    private static final LocalDate DAY_0 = LocalDate.of(2026, 6, 1);
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    RoomTypeInventoryJpaRepository jpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("save → findByKey: 필드 왕복 보존")
    void roundTrip() {
        RoomTypeInventoryRepositoryImpl repo = new RoomTypeInventoryRepositoryImpl(jpaRepository);
        RoomTypeInventory inv = RoomTypeInventory.create(HOTEL_ID, ROOM_TYPE_ID, DAY_0, FIXED);
        inv.addRoom(FIXED);
        inv.addRoom(FIXED);

        repo.save(inv);
        em.flush();
        em.clear();

        Optional<RoomTypeInventory> loaded = repo.findByKey(
            new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, DAY_0));
        assertThat(loaded).isPresent();
        assertThat(loaded.get().totalRooms()).isEqualTo(InventoryCount.of(2));
        assertThat(loaded.get().availableRooms()).isEqualTo(InventoryCount.of(2));
    }

    @Test
    @DisplayName("findRange: 구간 내 row 만 stayDate 오름차순으로 반환")
    void findRangeOrdersByDate() {
        RoomTypeInventoryRepositoryImpl repo = new RoomTypeInventoryRepositoryImpl(jpaRepository);
        for (int i = 0; i < 5; i++) {
            RoomTypeInventory inv = RoomTypeInventory.create(HOTEL_ID, ROOM_TYPE_ID, DAY_0.plusDays(i), FIXED);
            inv.addRoom(FIXED);
            repo.save(inv);
        }
        em.flush();
        em.clear();

        List<RoomTypeInventory> range = repo.findRange(HOTEL_ID, ROOM_TYPE_ID,
            DAY_0.plusDays(1), DAY_0.plusDays(3));

        assertThat(range).hasSize(3)
            .extracting(RoomTypeInventory::stayDate)
            .containsExactly(DAY_0.plusDays(1), DAY_0.plusDays(2), DAY_0.plusDays(3));
    }

    @Test
    @DisplayName("available > total 을 강제하면 CHECK 제약 위반으로 DB 가 차단")
    void checkConstraintRejectsInvalidRow() {
        RoomTypeInventoryRepositoryImpl repo = new RoomTypeInventoryRepositoryImpl(jpaRepository);
        RoomTypeInventory invalid = RoomTypeInventory.restore(
            new InventoryKey(HOTEL_ID, ROOM_TYPE_ID, DAY_0.plusDays(10)),
            InventoryCount.of(2),
            InventoryCount.of(2),
            0L,
            Instant.now(FIXED),
            Instant.now(FIXED));

        // 정상 저장 후 SQL 레벨로 available = 5, total = 2 로 변조해 CHECK 가 걸리는지 본다.
        // @DataJpaTest 슬라이스에는 PersistenceExceptionTranslator 가 적용되지 않아 Hibernate
        // 의 GenericJDBCException 이 그대로 올라온다. 메시지에 CHECK 이름이 포함되는 것으로
        // DB 레벨 가드가 작동했음을 증명한다.
        repo.save(invalid);
        em.flush();

        assertThatThrownBy(() -> {
            em.getEntityManager().createNativeQuery(
                "UPDATE room_type_inventory SET available_rooms = 5 WHERE stay_date = :d")
                .setParameter("d", DAY_0.plusDays(10))
                .executeUpdate();
            em.flush();
        }).isInstanceOf(GenericJDBCException.class)
          .hasMessageContaining("chk_inventory_available_le_total");
    }
}

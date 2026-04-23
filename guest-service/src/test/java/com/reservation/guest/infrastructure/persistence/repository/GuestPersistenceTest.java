package com.reservation.guest.infrastructure.persistence.repository;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.guest.domain.model.Email;
import com.reservation.guest.domain.model.Guest;
import com.reservation.guest.domain.model.GuestId;
import com.reservation.guest.domain.model.GuestName;
import com.reservation.guest.domain.model.PhoneNumber;
import org.hibernate.exception.ConstraintViolationException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * guest-service JPA 매핑 + Flyway V1 스키마 조합을 MySQL 컨테이너 위에서 검증한다.
 * rate-service RoomTypeRatePersistenceTest 와 동일 슬라이스 전략.
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
class GuestPersistenceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    GuestJpaRepository jpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("save → findById: 모든 필드 왕복 보존 (E.164 정규화 상태 포함)")
    void roundTrip() {
        GuestRepositoryImpl repo = new GuestRepositoryImpl(jpaRepository);
        Guest guest = Guest.create(
            new GuestName("길동", "홍"),
            new Email("hong@example.com"),
            new PhoneNumber("+82-10-1234-5678"),
            FIXED);

        repo.save(guest);
        em.flush();
        em.clear();

        Guest loaded = repo.findById(guest.id()).orElseThrow();
        assertThat(loaded.name()).isEqualTo(new GuestName("길동", "홍"));
        assertThat(loaded.email().value()).isEqualTo("hong@example.com");
        assertThat(loaded.phoneNumber().value()).isEqualTo("+821012345678");
    }

    @Test
    @DisplayName("existsByEmail: lowercase 정규화된 값으로 비교")
    void existsByEmailNormalizesCase() {
        GuestRepositoryImpl repo = new GuestRepositoryImpl(jpaRepository);
        repo.save(Guest.create(
            new GuestName("길동", "홍"),
            new Email("Hong@Example.COM"),
            new PhoneNumber("+82-10-1234-5678"),
            FIXED));
        em.flush();
        em.clear();

        assertThat(repo.existsByEmail(new Email("hong@example.com"))).isTrue();
        assertThat(repo.existsByEmail(new Email("HONG@example.com"))).isTrue();
        assertThat(repo.existsByEmail(new Email("other@example.com"))).isFalse();
    }

    @Test
    @DisplayName("UNIQUE(email) 위반은 ConstraintViolationException")
    void emailUniqueConstraint() {
        GuestRepositoryImpl repo = new GuestRepositoryImpl(jpaRepository);
        repo.save(Guest.create(
            new GuestName("길동", "홍"),
            new Email("dup@example.com"),
            new PhoneNumber("+82-10-1234-5678"),
            FIXED));
        em.flush();

        Guest dup = Guest.create(
            new GuestName("Alice", "Kim"),
            new Email("DUP@example.com"), // 대소문자만 달라도 UNIQUE 위반 (VO 가 lowercase 정규화)
            new PhoneNumber("+82-10-9999-0000"),
            FIXED);
        assertThatThrownBy(() -> {
            repo.save(dup);
            em.flush();
        }).isInstanceOf(ConstraintViolationException.class)
          .hasMessageContaining("uk_guest_email");
    }

    @Test
    @DisplayName("findAllByIds: 요청 id 중 존재분만 반환 (partial response)")
    void findAllByIdsReturnsOnlyExisting() {
        GuestRepositoryImpl repo = new GuestRepositoryImpl(jpaRepository);
        Guest g1 = Guest.create(
            new GuestName("길동", "홍"),
            new Email("a@example.com"),
            new PhoneNumber("+82-10-1111-1111"),
            FIXED);
        Guest g2 = Guest.create(
            new GuestName("Alice", "Kim"),
            new Email("b@example.com"),
            new PhoneNumber("+82-10-2222-2222"),
            FIXED);
        repo.save(g1);
        repo.save(g2);
        em.flush();
        em.clear();

        GuestId missing = GuestId.newId();
        List<Guest> results = repo.findAllByIds(List.of(g1.id(), g2.id(), missing));

        assertThat(results).hasSize(2)
            .extracting(g -> g.email().value())
            .containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }

    @Test
    @DisplayName("findAllByIds: 빈 입력은 빈 리스트 (DB 호출 없음 · JpaRepository 런타임 예외 회피)")
    void findAllByIdsEmptyInput() {
        GuestRepositoryImpl repo = new GuestRepositoryImpl(jpaRepository);

        assertThat(repo.findAllByIds(List.of())).isEmpty();
    }
}

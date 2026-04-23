package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.common.domain.UuidV7;
import com.reservation.common.messaging.outbox.OutboxMessage;
import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.reservation.infrastructure.persistence.entity.ReservationOutboxJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
class ReservationOutboxPersistenceTest {

    @Autowired
    ReservationOutboxJpaRepository jpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @Transactional
    @DisplayName("save 후 findUnpublished 가 동일 메시지를 반환, markPublished 후에는 제외")
    void saveFindMarkPublished() {
        ReservationOutboxRepositoryImpl repository =
            new ReservationOutboxRepositoryImpl(jpaRepository);

        OutboxMessage message = sample("reservation-events");
        repository.save(message);
        em.flush();
        em.clear();

        List<OutboxMessage> pending = repository.findUnpublished(10);
        assertThat(pending).extracting(OutboxMessage::id).containsExactly(message.id());

        Instant publishedAt = Instant.parse("2026-04-23T01:00:01Z");
        repository.markPublished(message.id(), publishedAt);
        em.flush();
        em.clear();

        assertThat(repository.findUnpublished(10)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("이미 발행된 row 에 markPublished 재호출은 멱등 — publishedAt 이 첫 값으로 유지")
    void markPublishedIsIdempotent() {
        ReservationOutboxRepositoryImpl repository =
            new ReservationOutboxRepositoryImpl(jpaRepository);
        OutboxMessage message = sample("reservation-events");
        repository.save(message);
        em.flush();
        em.clear();

        Instant first = Instant.parse("2026-04-23T01:00:01Z");
        repository.markPublished(message.id(), first);
        em.flush();
        em.clear();

        // 두 번째 호출이 throw 하지 않으며, 더 늦은 시각으로 덮어쓰지도 않아야 한다.
        Instant second = Instant.parse("2026-04-23T01:00:02Z");
        repository.markPublished(message.id(), second);
        em.flush();
        em.clear();

        ReservationOutboxJpaEntity reloaded = jpaRepository.findById(message.id()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isEqualTo(first);
    }

    private OutboxMessage sample(String topic) {
        Instant now = Instant.parse("2026-04-23T01:00:00Z");
        return new OutboxMessage(
            UuidV7.create(),
            topic,
            "ReservationCreatedEvent",
            UuidV7.create(),
            now,
            "01933333-1111-7aaa-9aaa-000000000001",
            "{\"payload\":\"sample\"}".getBytes(),
            now,
            null
        );
    }
}

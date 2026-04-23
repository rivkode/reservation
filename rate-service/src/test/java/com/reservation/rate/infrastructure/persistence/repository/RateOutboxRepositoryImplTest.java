package com.reservation.rate.infrastructure.persistence.repository;

import com.reservation.common.messaging.outbox.OutboxMessage;
import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rate-service 의 Outbox 구현을 실제 MySQL 위에서 검증한다.
 * hotel-service 의 HotelOutboxRepositoryImplTest 와 동일 계약.
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
class RateOutboxRepositoryImplTest {

    @Autowired
    RateOutboxJpaRepository jpaRepository;

    @Test
    @DisplayName("save → findUnpublished: 발행 전 메시지 반환 · markPublished 후 제외")
    void saveFindAndMark() {
        RateOutboxRepositoryImpl repo = new RateOutboxRepositoryImpl(jpaRepository);
        Instant now = Instant.parse("2026-04-23T10:00:00Z");

        OutboxMessage m1 = message(UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(10));
        OutboxMessage m2 = message(UUID.randomUUID(), UUID.randomUUID(), now);
        repo.save(m1);
        repo.save(m2);

        List<OutboxMessage> pending = repo.findUnpublished(10);
        assertThat(pending).hasSize(2);
        assertThat(pending.get(0).createdAt()).isBefore(pending.get(1).createdAt());

        repo.markPublished(m1.id(), now.plusSeconds(1));
        List<OutboxMessage> afterMark = repo.findUnpublished(10);
        assertThat(afterMark).hasSize(1).extracting(OutboxMessage::id).containsExactly(m2.id());
    }

    @Test
    @DisplayName("markPublished: 이미 발행된 id 에 재호출해도 예외 없음 (멱등)")
    void markPublishedIdempotent() {
        RateOutboxRepositoryImpl repo = new RateOutboxRepositoryImpl(jpaRepository);
        Instant now = Instant.parse("2026-04-23T10:00:00Z");
        OutboxMessage m = message(UUID.randomUUID(), UUID.randomUUID(), now);
        repo.save(m);

        repo.markPublished(m.id(), now.plusSeconds(1));
        repo.markPublished(m.id(), now.plusSeconds(2));

        assertThat(repo.findUnpublished(10)).isEmpty();
    }

    @Test
    @DisplayName("findUnpublished(limit): limit 이하로 제한 + createdAt ASC 순서 (FIFO 계약)")
    void findUnpublishedRespectsLimit() {
        RateOutboxRepositoryImpl repo = new RateOutboxRepositoryImpl(jpaRepository);
        Instant now = Instant.parse("2026-04-23T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            repo.save(message(UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(i)));
        }

        List<OutboxMessage> first3 = repo.findUnpublished(3);

        assertThat(first3).hasSize(3);
        // Outbox Relay 는 오래된 메시지부터 처리하는 FIFO 계약 — 정렬 순서까지 검증.
        assertThat(first3).extracting(OutboxMessage::createdAt)
            .containsExactly(now, now.plusSeconds(1), now.plusSeconds(2));
    }

    private OutboxMessage message(UUID id, UUID eventId, Instant createdAt) {
        return new OutboxMessage(id, "rate-events", "RoomTypeRateChangedEvent",
            eventId, createdAt, "H-1", new byte[]{1, 2, 3}, createdAt, null);
    }
}

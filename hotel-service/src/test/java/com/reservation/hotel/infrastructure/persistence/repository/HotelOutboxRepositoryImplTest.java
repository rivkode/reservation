package com.reservation.hotel.infrastructure.persistence.repository;

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
 * hotel-service 의 Outbox 구현을 실제 MySQL 위에서 검증한다. findUnpublished 가
 * published_at IS NULL 필터 + created_at ASC 정렬을 지키는지, markPublished 멱등성이
 * 보장되는지 확인.
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
class HotelOutboxRepositoryImplTest {

    @Autowired
    HotelOutboxJpaRepository jpaRepository;

    @Test
    @DisplayName("save → findUnpublished 가 발행 전 메시지 반환, markPublished 후에는 제외")
    void saveFindAndMark() {
        HotelOutboxRepositoryImpl repo = new HotelOutboxRepositoryImpl(jpaRepository);
        Instant now = Instant.parse("2026-04-22T10:00:00Z");

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
    @DisplayName("markPublished 는 멱등 — 이미 발행된 id 에 재호출해도 예외 없음")
    void markPublishedIdempotent() {
        HotelOutboxRepositoryImpl repo = new HotelOutboxRepositoryImpl(jpaRepository);
        Instant now = Instant.parse("2026-04-22T10:00:00Z");
        OutboxMessage m = message(UUID.randomUUID(), UUID.randomUUID(), now);
        repo.save(m);

        repo.markPublished(m.id(), now.plusSeconds(1));
        repo.markPublished(m.id(), now.plusSeconds(2));

        assertThat(repo.findUnpublished(10)).isEmpty();
    }

    @Test
    @DisplayName("findUnpublished(limit) 은 limit 이하로 제한")
    void findUnpublishedRespectsLimit() {
        HotelOutboxRepositoryImpl repo = new HotelOutboxRepositoryImpl(jpaRepository);
        Instant now = Instant.parse("2026-04-22T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            repo.save(message(UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(i)));
        }

        assertThat(repo.findUnpublished(3)).hasSize(3);
    }

    private OutboxMessage message(UUID id, UUID eventId, Instant createdAt) {
        return new OutboxMessage(id, "hotel-events", "RoomCreatedEvent",
            eventId, createdAt, "H-1", new byte[]{1, 2, 3}, createdAt, null);
    }
}

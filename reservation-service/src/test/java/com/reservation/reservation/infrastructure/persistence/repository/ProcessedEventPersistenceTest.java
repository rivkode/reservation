package com.reservation.reservation.infrastructure.persistence.repository;

import com.reservation.common.persistence.UuidBinaryConverter;
import com.reservation.common.test.MysqlContainerExtension;
import com.reservation.reservation.infrastructure.messaging.JpaProcessedEventStore;
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

import java.time.Instant;
import java.util.UUID;

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
class ProcessedEventPersistenceTest {

    @Autowired
    ProcessedEventJpaRepository jpaRepository;
    @Autowired
    TestEntityManager em;

    @Test
    @DisplayName("markProcessed 후 isAlreadyProcessed 가 true")
    void marksAndChecks() {
        JpaProcessedEventStore store = new JpaProcessedEventStore(jpaRepository, em.getEntityManager());
        UUID eventId = UUID.randomUUID();

        assertThat(store.isAlreadyProcessed(eventId)).isFalse();

        store.markProcessed(eventId, "RoomCreatedEvent", Instant.now());
        em.flush();
        em.clear();

        assertThat(store.isAlreadyProcessed(eventId)).isTrue();
    }

    @Test
    @DisplayName("같은 eventId 를 flush 된 상태에서 다시 persist 하면 PK 충돌 — 동시성 가드")
    void duplicateEventIdRejected() {
        JpaProcessedEventStore store = new JpaProcessedEventStore(jpaRepository, em.getEntityManager());
        UUID eventId = UUID.randomUUID();

        store.markProcessed(eventId, "RoomCreatedEvent", Instant.now());
        em.flush();
        em.clear();

        // @DataJpaTest 슬라이스에는 PersistenceExceptionTranslator 가 적용되지 않아
        // Hibernate 의 ConstraintViolationException 이 그대로 올라온다. 운영 런타임 (full
        // Spring 컨텍스트) 에서는 같은 예외가 DataIntegrityViolationException 으로 변환되지만,
        // 두 경우 모두 "중복 eventId 커밋 불가" 라는 계약을 동일하게 만족한다.
        assertThatThrownBy(() -> {
            store.markProcessed(eventId, "RoomCreatedEvent", Instant.now());
            em.flush();
        }).isInstanceOf(ConstraintViolationException.class)
          .hasMessageContaining("processed_events");
    }
}

package com.reservation.common.messaging.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxMessageTest {

    private static final UUID ID = UUID.fromString("018f4a9b-2c4d-7c34-8e9a-000000000000");
    private static final UUID EVENT_ID = UUID.fromString("018f4a9b-2c4d-7c34-8e9a-000000000001");
    private static final Instant NOW = Instant.parse("2026-04-22T10:00:00Z");

    @Test
    @DisplayName("publishedAt 이 null 이면 isUnpublished 는 true")
    void isUnpublishedWhenPublishedAtIsNull() {
        OutboxMessage message = newMessage(null);
        assertThat(message.isUnpublished()).isTrue();
    }

    @Test
    @DisplayName("publishedAt 이 설정되면 isUnpublished 는 false")
    void isUnpublishedFalseWhenPublishedAtSet() {
        OutboxMessage message = newMessage(NOW.plusSeconds(1));
        assertThat(message.isUnpublished()).isFalse();
    }

    @Test
    @DisplayName("publishedAt 외 필수 필드가 null 이면 NPE 로 빠르게 실패")
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new OutboxMessage(
            null, "t", "e", EVENT_ID, NOW, "k", new byte[0], NOW, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("id");

        assertThatThrownBy(() -> new OutboxMessage(
            ID, null, "e", EVENT_ID, NOW, "k", new byte[0], NOW, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("topic");

        assertThatThrownBy(() -> new OutboxMessage(
            ID, "t", null, EVENT_ID, NOW, "k", new byte[0], NOW, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("eventType");

        assertThatThrownBy(() -> new OutboxMessage(
            ID, "t", "e", null, NOW, "k", new byte[0], NOW, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("eventId");

        assertThatThrownBy(() -> new OutboxMessage(
            ID, "t", "e", EVENT_ID, null, "k", new byte[0], NOW, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("occurredAt");

        assertThatThrownBy(() -> new OutboxMessage(
            ID, "t", "e", EVENT_ID, NOW, null, new byte[0], NOW, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("partitionKey");

        assertThatThrownBy(() -> new OutboxMessage(
            ID, "t", "e", EVENT_ID, NOW, "k", null, NOW, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("payload");

        assertThatThrownBy(() -> new OutboxMessage(
            ID, "t", "e", EVENT_ID, NOW, "k", new byte[0], null, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("createdAt");
    }

    private OutboxMessage newMessage(Instant publishedAt) {
        return new OutboxMessage(
            ID, "hotel-events", "RoomCreatedEvent",
            EVENT_ID, NOW, "H-1", new byte[]{1, 2, 3}, NOW, publishedAt
        );
    }
}

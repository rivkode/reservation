package com.reservation.contracts.event;

import com.reservation.contracts.event.billing.BillingCreatedEvent;
import com.reservation.contracts.event.billing.BillingCreationFailedEvent;
import com.reservation.contracts.event.hotel.RoomCreatedEvent;
import com.reservation.contracts.event.hotel.RoomDeletedEvent;
import com.reservation.contracts.event.hotel.RoomUpdatedEvent;
import com.reservation.contracts.event.rate.RoomTypeRateChangedEvent;
import com.reservation.contracts.event.reservation.ReservationCancelledEvent;
import com.reservation.contracts.event.reservation.ReservationCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventContractTest {

    // UUID v7 형태 (timestamp prefix + version nibble '7'). PRD 방침상 reservationId 가 v7 이므로
    // 이벤트 fixture 도 같은 모양으로 맞춰 의도를 표현한다.
    private static final UUID EVENT_ID = UUID.fromString("018f4a9b-2c4d-7c34-8e9a-000000000000");
    private static final Instant OCCURRED_AT = Instant.parse("2026-04-21T10:00:00Z");
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-06-01");
    private static final LocalDate CHECK_OUT = LocalDate.parse("2026-06-03");

    // 신규 이벤트 record 가 contracts/event/** 아래 추가되면 반드시 본 Stream 과
    // allContractEventFactoriesWithNullEventId() 에도 등록할 것. 자동 스캔(ClassGraph 등)은
    // contracts 모듈의 의존성 최소화 원칙상 도입하지 않았다.
    static Stream<DomainEvent> allContractEvents() {
        return Stream.of(
            new RoomCreatedEvent(EVENT_ID, OCCURRED_AT, "H-1", "R-1", "RT-1"),
            new RoomUpdatedEvent(EVENT_ID, OCCURRED_AT, "H-1", "R-1", "RT-1"),
            new RoomDeletedEvent(EVENT_ID, OCCURRED_AT, "H-1", "R-1", "RT-1"),
            new RoomTypeRateChangedEvent(EVENT_ID, OCCURRED_AT, "H-1", "RT-1", CHECK_IN, 150_000L, "KRW"),
            new ReservationCreatedEvent(EVENT_ID, OCCURRED_AT, "RSV-1", "H-1", "RT-1", "G-1", CHECK_IN, CHECK_OUT, 2, 300_000L, "KRW"),
            new ReservationCancelledEvent(EVENT_ID, OCCURRED_AT, "RSV-1", "H-1", "RT-1", CHECK_IN, CHECK_OUT),
            new BillingCreatedEvent(EVENT_ID, OCCURRED_AT, "RSV-1", "B-1", 300_000L, "KRW"),
            new BillingCreationFailedEvent(EVENT_ID, OCCURRED_AT, "RSV-1", "PAYMENT_GATEWAY_TIMEOUT")
        );
    }

    static Stream<Supplier<DomainEvent>> allContractEventFactoriesWithNullEventId() {
        return Stream.of(
            () -> new RoomCreatedEvent(null, OCCURRED_AT, "H-1", "R-1", "RT-1"),
            () -> new RoomUpdatedEvent(null, OCCURRED_AT, "H-1", "R-1", "RT-1"),
            () -> new RoomDeletedEvent(null, OCCURRED_AT, "H-1", "R-1", "RT-1"),
            () -> new RoomTypeRateChangedEvent(null, OCCURRED_AT, "H-1", "RT-1", CHECK_IN, 150_000L, "KRW"),
            () -> new ReservationCreatedEvent(null, OCCURRED_AT, "RSV-1", "H-1", "RT-1", "G-1", CHECK_IN, CHECK_OUT, 2, 300_000L, "KRW"),
            () -> new ReservationCancelledEvent(null, OCCURRED_AT, "RSV-1", "H-1", "RT-1", CHECK_IN, CHECK_OUT),
            () -> new BillingCreatedEvent(null, OCCURRED_AT, "RSV-1", "B-1", 300_000L, "KRW"),
            () -> new BillingCreationFailedEvent(null, OCCURRED_AT, "RSV-1", "REASON")
        );
    }

    @ParameterizedTest
    @MethodSource("allContractEvents")
    @DisplayName("모든 계약 이벤트는 DomainEvent 를 구현하고 eventId · occurredAt 을 노출한다")
    void implementsDomainEventContract(DomainEvent event) {
        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @ParameterizedTest
    @MethodSource("allContractEventFactoriesWithNullEventId")
    @DisplayName("eventId 가 null 이면 생성 시점에 NullPointerException 으로 빠르게 실패한다")
    void rejectsNullEventId(Supplier<DomainEvent> factory) {
        assertThatThrownBy(factory::get)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("eventId");
    }

    // record 의 equals / hashCode 는 JDK 가 모든 record 에 동일 규칙으로 생성하므로 8 종을
    // 전수 검증하지 않고 대표 1 건(RoomCreatedEvent)만 확인한다.
    @Test
    @DisplayName("record 는 값 동등성을 가지므로 동일 필드 두 인스턴스는 equals · hashCode 가 일치한다")
    void recordsAreValueEqual() {
        RoomCreatedEvent first = new RoomCreatedEvent(EVENT_ID, OCCURRED_AT, "H-1", "R-1", "RT-1");
        RoomCreatedEvent second = new RoomCreatedEvent(EVENT_ID, OCCURRED_AT, "H-1", "R-1", "RT-1");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}

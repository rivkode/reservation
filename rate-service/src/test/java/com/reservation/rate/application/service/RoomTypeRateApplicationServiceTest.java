package com.reservation.rate.application.service;

import com.reservation.common.messaging.outbox.OutboxEventPublisher;
import com.reservation.contracts.event.DomainEvent;
import com.reservation.contracts.event.rate.RoomTypeRateChangedEvent;
import com.reservation.rate.application.dto.ChangeRoomTypeRateCommand;
import com.reservation.rate.application.dto.RegisterRoomTypeRateCommand;
import com.reservation.rate.application.dto.RoomTypeRateResult;
import com.reservation.rate.domain.exception.DuplicateRateException;
import com.reservation.rate.domain.exception.RateNotFoundException;
import com.reservation.rate.domain.model.HotelId;
import com.reservation.rate.domain.model.Money;
import com.reservation.rate.domain.model.RateId;
import com.reservation.rate.domain.model.RoomTypeId;
import com.reservation.rate.domain.model.RoomTypeRate;
import com.reservation.rate.domain.repository.RoomTypeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomTypeRateApplicationServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
    private static final String TOPIC = "rate-events";
    private static final HotelId HOTEL = HotelId.of(UUID.fromString("01970000-0000-7000-8000-000000000001"));
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of(UUID.fromString("01970000-0000-7000-8000-000000000002"));
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);

    private RoomTypeRateRepository repository;
    private OutboxEventPublisher outbox;
    private RoomTypeRateApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(RoomTypeRateRepository.class);
        outbox = mock(OutboxEventPublisher.class);
        service = new RoomTypeRateApplicationService(repository, outbox, FIXED);
    }

    @Test
    @DisplayName("register: 저장 후 Result 반환 — 이벤트는 발행하지 않음 (FR-R-03 은 변경 시에만)")
    void registerDoesNotPublishEvent() {
        when(repository.existsByNaturalKey(HOTEL, ROOM_TYPE, DATE)).thenReturn(false);
        when(repository.save(any(RoomTypeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomTypeRateResult result = service.register(
            new RegisterRoomTypeRateCommand(HOTEL.asString(), ROOM_TYPE.asString(), DATE, 150_000L, "KRW"));

        assertThat(result.hotelId()).isEqualTo(HOTEL.asString());
        assertThat(result.amount()).isEqualTo(150_000L);
        assertThat(result.currency()).isEqualTo("KRW");

        ArgumentCaptor<RoomTypeRate> captor = ArgumentCaptor.forClass(RoomTypeRate.class);
        verify(repository).save(captor.capture());
        RoomTypeRate saved = captor.getValue();
        assertThat(saved.hotelId()).isEqualTo(HOTEL);
        assertThat(saved.roomTypeId()).isEqualTo(ROOM_TYPE);
        assertThat(saved.date()).isEqualTo(DATE);
        assertThat(saved.money()).isEqualTo(Money.of(150_000L, "KRW"));
        assertThat(saved.version()).isZero();

        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("register: 자연키 중복이면 DuplicateRateException + save 없음")
    void registerRejectsDuplicate() {
        when(repository.existsByNaturalKey(HOTEL, ROOM_TYPE, DATE)).thenReturn(true);

        assertThatThrownBy(() -> service.register(
            new RegisterRoomTypeRateCommand(HOTEL.asString(), ROOM_TYPE.asString(), DATE, 150_000L, "KRW")))
            .isInstanceOf(DuplicateRateException.class);
        verify(repository, never()).save(any());
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("register: 음수 금액은 Money VO 가 IllegalArgumentException 으로 거부 (existsByNaturalKey 이전)")
    void registerRejectsNegativeAmount() {
        // Money.of(-1L, "KRW") 가 existsByNaturalKey 호출 이전에 throw 되므로 stub 불필요.
        assertThatThrownBy(() -> service.register(
            new RegisterRoomTypeRateCommand(HOTEL.asString(), ROOM_TYPE.asString(), DATE, -1L, "KRW")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).existsByNaturalKey(any(), any(), any());
    }

    @Test
    @DisplayName("change: 다른 금액이면 AR 상태 변경 + save · RoomTypeRateChangedEvent(occurredAt=now) 발행")
    void changePublishesEventWhenAmountDiffers() {
        RoomTypeRate existing = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(150_000L, "KRW"), FIXED);
        Instant before = existing.updatedAt();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.save(any(RoomTypeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.change(new ChangeRoomTypeRateCommand(existing.id().asString(), 180_000L, "KRW"));

        // AR 상태 변경 확인 (도메인이 실제로 변경을 반영)
        assertThat(existing.money()).isEqualTo(Money.of(180_000L, "KRW"));
        assertThat(existing.updatedAt()).isEqualTo(before); // FIXED Clock 이라 같은 instant — 시점 식별용
        // 같은 AR 인스턴스가 그대로 save 됨을 확인
        verify(repository).save(existing);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).publish(captor.capture(), eq(TOPIC), eq(HOTEL.asString()));
        assertThat(captor.getValue()).isInstanceOf(RoomTypeRateChangedEvent.class);
        RoomTypeRateChangedEvent event = (RoomTypeRateChangedEvent) captor.getValue();
        assertThat(event.hotelId()).isEqualTo(HOTEL.asString());
        assertThat(event.roomTypeId()).isEqualTo(ROOM_TYPE.asString());
        assertThat(event.date()).isEqualTo(DATE);
        assertThat(event.amount()).isEqualTo(180_000L);
        assertThat(event.currency()).isEqualTo("KRW");
        assertThat(event.occurredAt()).isEqualTo(FIXED.instant());
    }

    @Test
    @DisplayName("change: 동일 금액이면 no-op — 이벤트 발행 · save 둘 다 생략, AR 상태 유지")
    void changeSkipsEventOnNoop() {
        RoomTypeRate existing = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(150_000L, "KRW"), FIXED);
        Instant beforeUpdatedAt = existing.updatedAt();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));

        service.change(new ChangeRoomTypeRateCommand(existing.id().asString(), 150_000L, "KRW"));

        // no-op: AR 상태 변경 없음
        assertThat(existing.money()).isEqualTo(Money.of(150_000L, "KRW"));
        assertThat(existing.updatedAt()).isEqualTo(beforeUpdatedAt);
        // no-op: DB write · 이벤트 발행 모두 skip
        verify(repository, never()).save(any());
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("change: rateId 미존재 시 RateNotFoundException + 이벤트 없음")
    void changeRejectsUnknownId() {
        RateId id = RateId.newId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.change(new ChangeRoomTypeRateCommand(id.asString(), 100L, "KRW")))
            .isInstanceOf(RateNotFoundException.class);
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("findById: Result 로 변환")
    void findByIdReturnsResult() {
        RoomTypeRate existing = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(150_000L, "KRW"), FIXED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));

        RoomTypeRateResult result = service.findById(existing.id().asString());

        assertThat(result.id()).isEqualTo(existing.id().asString());
        assertThat(result.amount()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("findByRange: repository 결과를 Result 리스트로 매핑")
    void findByRangeMapsAll() {
        RoomTypeRate r1 = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE, Money.of(150_000L, "KRW"), FIXED);
        RoomTypeRate r2 = RoomTypeRate.create(HOTEL, ROOM_TYPE, DATE.plusDays(1), Money.of(170_000L, "KRW"), FIXED);
        when(repository.findByRange(HOTEL, ROOM_TYPE, DATE, DATE.plusDays(1)))
            .thenReturn(List.of(r1, r2));

        List<RoomTypeRateResult> results = service.findByRange(
            HOTEL.asString(), ROOM_TYPE.asString(), DATE, DATE.plusDays(1));

        assertThat(results).hasSize(2)
            .extracting(RoomTypeRateResult::amount)
            .containsExactly(150_000L, 170_000L);
    }

    @Test
    @DisplayName("findByRange: from > to 이면 IllegalArgumentException (400 매핑)")
    void findByRangeRejectsInvertedRange() {
        assertThatThrownBy(() -> service.findByRange(
            HOTEL.asString(), ROOM_TYPE.asString(), DATE.plusDays(1), DATE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findByRange: 범위가 MAX_RANGE_DAYS 를 넘으면 IllegalArgumentException (DoS 방어)")
    void findByRangeRejectsWideRange() {
        assertThatThrownBy(() -> service.findByRange(
            HOTEL.asString(), ROOM_TYPE.asString(),
            DATE, DATE.plusDays(RoomTypeRateApplicationService.MAX_RANGE_DAYS + 1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("range too wide");
    }

    @Test
    @DisplayName("findByRange: 범위 = MAX_RANGE_DAYS 인 경계 값은 허용")
    void findByRangeAllowsMaxBoundary() {
        when(repository.findByRange(any(), any(), any(), any())).thenReturn(List.of());

        List<RoomTypeRateResult> results = service.findByRange(
            HOTEL.asString(), ROOM_TYPE.asString(),
            DATE, DATE.plusDays(RoomTypeRateApplicationService.MAX_RANGE_DAYS));

        assertThat(results).isEmpty();
    }
}

package com.reservation.reservation.application.service;

import com.reservation.common.messaging.outbox.OutboxEventPublisher;
import com.reservation.contracts.event.billing.BillingCreationFailedEvent;
import com.reservation.contracts.event.reservation.ReservationCancelledEvent;
import com.reservation.reservation.application.dto.CancelReservationResult;
import com.reservation.reservation.application.idempotency.ProcessedEventStore;
import com.reservation.reservation.domain.exception.InventoryNotInitializedException;
import com.reservation.reservation.domain.exception.ReservationAlreadyCancelledException;
import com.reservation.reservation.domain.exception.ReservationNotFoundException;
import com.reservation.reservation.domain.model.BillingQuote;
import com.reservation.reservation.domain.model.CancellationReason;
import com.reservation.reservation.domain.model.GuestId;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryCount;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.NumberOfGuests;
import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationId;
import com.reservation.reservation.domain.model.ReservationStatus;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.model.StayPeriod;
import com.reservation.reservation.domain.model.TwentyFourHourCancellationPolicy;
import com.reservation.reservation.domain.repository.ReservationRepository;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CancelReservationApplicationService")
class CancelReservationApplicationServiceTest {

    private static final HotelId HOTEL = HotelId.of("01933333-1111-7aaa-9aaa-000000000001");
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of("01933333-1111-7aaa-9aaa-000000000002");
    private static final GuestId GUEST = GuestId.of("01933333-1111-7aaa-9aaa-000000000003");
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-06-01");
    private static final LocalDate CHECK_OUT = LocalDate.parse("2026-06-03");
    // 본 PR 단위 테스트는 정책 분기를 검증하지 않음 — 24h 전이라 환불 100% 받는 시각으로 고정.
    private static final Instant CANCEL_AT = Instant.parse("2026-04-23T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CANCEL_AT, ZoneOffset.UTC);
    private static final BillingQuote QUOTE =
        new BillingQuote(Money.of(300_000L, "KRW"), CANCEL_AT.minusSeconds(3600));

    private ReservationRepository reservationRepository;
    private RoomTypeInventoryRepository inventoryRepository;
    private OutboxEventPublisher outboxEventPublisher;
    private ProcessedEventStore processedEventStore;
    private TransactionTemplate transactionTemplate;

    private CancelReservationApplicationService service;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        inventoryRepository = mock(RoomTypeInventoryRepository.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        processedEventStore = mock(ProcessedEventStore.class);
        transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> cb =
                invocation.getArgument(0);
            cb.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new CancelReservationApplicationService(
            reservationRepository,
            inventoryRepository,
            new TwentyFourHourCancellationPolicy(),
            outboxEventPublisher,
            processedEventStore,
            transactionTemplate,
            CLOCK
        );

        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("cancelByUser 정상: Reservation.cancel + 2일치 inventory.release + Outbox publish")
    void cancelByUserHappyPath() {
        Reservation reservation = givenConfirmedReservation();
        givenInventory(CHECK_IN, 5, 3);
        givenInventory(CHECK_IN.plusDays(1), 5, 4);

        CancelReservationResult result = service.cancelByUser(reservation.id());

        assertThat(result.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.cancelledAt()).isEqualTo(CANCEL_AT);
        assertThat(result.refundRate()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(result.cancellationPolicyName()).isEqualTo(TwentyFourHourCancellationPolicy.NAME);

        ArgumentCaptor<RoomTypeInventory> inventoryCaptor =
            ArgumentCaptor.forClass(RoomTypeInventory.class);
        verify(inventoryRepository, times(2)).save(inventoryCaptor.capture());
        assertThat(inventoryCaptor.getAllValues())
            .extracting(RoomTypeInventory::stayDate)
            .containsExactlyInAnyOrder(CHECK_IN, CHECK_IN.plusDays(1));
        assertThat(inventoryCaptor.getAllValues())
            .extracting(inv -> inv.availableRooms().value())
            // 초기 available 3,4 → release 후 4,5 (each +1)
            .containsExactlyInAnyOrder(4, 5);

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        Reservation saved = reservationCaptor.getValue();
        assertThat(saved.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(saved.cancellation()).isPresent();
        assertThat(saved.cancellation().orElseThrow().reason())
            .isEqualTo(CancellationReason.USER_REQUEST);

        ArgumentCaptor<ReservationCancelledEvent> eventCaptor =
            ArgumentCaptor.forClass(ReservationCancelledEvent.class);
        verify(outboxEventPublisher).publish(eventCaptor.capture(), eq("reservation-events"),
            eq(HOTEL.asString()));
        ReservationCancelledEvent event = eventCaptor.getValue();
        assertThat(event.reservationId()).isEqualTo(reservation.id().asString());
        assertThat(event.checkInDate()).isEqualTo(CHECK_IN);
        assertThat(event.checkOutDate()).isEqualTo(CHECK_OUT);
    }

    @Test
    @DisplayName("cancelByUser: 미존재 reservation 은 ReservationNotFoundException")
    void cancelByUserNotFound() {
        ReservationId id = ReservationId.newId();
        when(reservationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ReservationNotFoundException.class)
            .isThrownBy(() -> service.cancelByUser(id));

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("cancelByUser: 이미 CANCELLED 면 ReservationAlreadyCancelledException")
    void cancelByUserAlreadyCancelled() {
        Reservation reservation = givenConfirmedReservation();
        reservation.cancel(new TwentyFourHourCancellationPolicy(),
            CancellationReason.USER_REQUEST, CLOCK);
        when(reservationRepository.findById(reservation.id())).thenReturn(Optional.of(reservation));

        assertThatExceptionOfType(ReservationAlreadyCancelledException.class)
            .isThrownBy(() -> service.cancelByUser(reservation.id()));

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("cancelByUser: inventory row 없으면 InventoryNotInitializedException — 트랜잭션 롤백 대상")
    void cancelByUserMissingInventory() {
        Reservation reservation = givenConfirmedReservation();
        when(inventoryRepository.findByKey(any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(InventoryNotInitializedException.class)
            .isThrownBy(() -> service.cancelByUser(reservation.id()));

        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("cancelOnBillingFailure 정상: cancel + release + Outbox + processed_events 기록")
    void cancelOnBillingFailureHappyPath() {
        Reservation reservation = givenConfirmedReservation();
        givenInventory(CHECK_IN, 5, 3);
        givenInventory(CHECK_IN.plusDays(1), 5, 4);
        BillingCreationFailedEvent event = new BillingCreationFailedEvent(
            UUID.randomUUID(), CANCEL_AT, reservation.id().asString(), "rate-svc-down");
        when(processedEventStore.isAlreadyProcessed(event.eventId())).thenReturn(false);

        service.cancelOnBillingFailure(event);

        verify(inventoryRepository, times(2)).save(any());
        ArgumentCaptor<Reservation> savedCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().cancellation().orElseThrow().reason())
            .isEqualTo(CancellationReason.BILLING_FAILED);
        verify(outboxEventPublisher).publish(any(), eq("reservation-events"), eq(HOTEL.asString()));
        verify(processedEventStore).markProcessed(eq(event.eventId()),
            eq("BillingCreationFailedEvent"), any());
    }

    @Test
    @DisplayName("cancelOnBillingFailure: 이미 처리된 eventId 면 모두 skip (markProcessed 도 호출 X)")
    void cancelOnBillingFailureAlreadyProcessed() {
        BillingCreationFailedEvent event = new BillingCreationFailedEvent(
            UUID.randomUUID(), CANCEL_AT, ReservationId.newId().asString(), "x");
        when(processedEventStore.isAlreadyProcessed(event.eventId())).thenReturn(true);

        service.cancelOnBillingFailure(event);

        verify(reservationRepository, never()).findById(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
        verify(processedEventStore, never()).markProcessed(any(), any(), any());
    }

    @Test
    @DisplayName("cancelOnBillingFailure: reservation 미존재 → noop + processed 기록 (재시도 루프 차단)")
    void cancelOnBillingFailureNoopWhenReservationMissing() {
        BillingCreationFailedEvent event = new BillingCreationFailedEvent(
            UUID.randomUUID(), CANCEL_AT, ReservationId.newId().asString(), "x");
        when(processedEventStore.isAlreadyProcessed(event.eventId())).thenReturn(false);
        when(reservationRepository.findById(any())).thenReturn(Optional.empty());

        service.cancelOnBillingFailure(event);

        verify(inventoryRepository, never()).findByKey(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
        verify(processedEventStore).markProcessed(eq(event.eventId()),
            eq("BillingCreationFailedEvent"), any());
    }

    @Test
    @DisplayName("cancelOnBillingFailure: InventoryNotInitializedException 은 비-흡수 — 전파 + markProcessed 미호출 (재시도 가치 있음)")
    void cancelOnBillingFailurePropagatesWhenInventoryMissing() {
        Reservation reservation = givenConfirmedReservation();
        when(inventoryRepository.findByKey(any())).thenReturn(Optional.empty());
        BillingCreationFailedEvent event = new BillingCreationFailedEvent(
            UUID.randomUUID(), CANCEL_AT, reservation.id().asString(), "rate-svc-down");
        when(processedEventStore.isAlreadyProcessed(event.eventId())).thenReturn(false);

        assertThatExceptionOfType(InventoryNotInitializedException.class)
            .isThrownBy(() -> service.cancelOnBillingFailure(event));

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
        verify(processedEventStore, never()).markProcessed(any(), any(), any());
    }

    @Test
    @DisplayName("cancelOnBillingFailure: InventoryReleaseExceedsCapacityException 은 비-흡수 — 정합성 시그널 (운영 대응)")
    void cancelOnBillingFailurePropagatesWhenReleaseExceedsCapacity() {
        Reservation reservation = givenConfirmedReservation();
        // total=3, available=3 (이미 가득) — release 시 capacity 초과
        givenInventory(CHECK_IN, 3, 3);
        BillingCreationFailedEvent event = new BillingCreationFailedEvent(
            UUID.randomUUID(), CANCEL_AT, reservation.id().asString(), "rate-svc-down");
        when(processedEventStore.isAlreadyProcessed(event.eventId())).thenReturn(false);

        assertThatExceptionOfType(
                com.reservation.reservation.domain.exception.InventoryReleaseExceedsCapacityException.class)
            .isThrownBy(() -> service.cancelOnBillingFailure(event));

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
        verify(processedEventStore, never()).markProcessed(any(), any(), any());
    }

    @Test
    @DisplayName("cancelOnBillingFailure: 이미 CANCELLED 면 noop + processed 기록")
    void cancelOnBillingFailureNoopWhenAlreadyCancelled() {
        Reservation reservation = givenConfirmedReservation();
        reservation.cancel(new TwentyFourHourCancellationPolicy(),
            CancellationReason.USER_REQUEST, CLOCK);
        BillingCreationFailedEvent event = new BillingCreationFailedEvent(
            UUID.randomUUID(), CANCEL_AT, reservation.id().asString(), "x");
        when(processedEventStore.isAlreadyProcessed(event.eventId())).thenReturn(false);
        when(reservationRepository.findById(reservation.id())).thenReturn(Optional.of(reservation));

        service.cancelOnBillingFailure(event);

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
        verify(processedEventStore).markProcessed(eq(event.eventId()),
            eq("BillingCreationFailedEvent"), any());
    }

    private Reservation givenConfirmedReservation() {
        Reservation reservation = Reservation.create(
            HOTEL, ROOM_TYPE, GUEST,
            new StayPeriod(CHECK_IN, CHECK_OUT),
            NumberOfGuests.of(2), QUOTE, CLOCK);
        when(reservationRepository.findById(reservation.id())).thenReturn(Optional.of(reservation));
        return reservation;
    }

    private void givenInventory(LocalDate date, int total, int available) {
        InventoryKey key = new InventoryKey(HOTEL, ROOM_TYPE, date);
        RoomTypeInventory inventory = RoomTypeInventory.restore(
            key, InventoryCount.of(total), InventoryCount.of(available),
            0L, CANCEL_AT, CANCEL_AT);
        when(inventoryRepository.findByKey(key)).thenReturn(Optional.of(inventory));
    }
}

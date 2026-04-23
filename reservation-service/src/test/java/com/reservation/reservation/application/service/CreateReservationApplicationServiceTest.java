package com.reservation.reservation.application.service;

import com.reservation.common.messaging.outbox.OutboxEventPublisher;
import com.reservation.contracts.event.reservation.ReservationCreatedEvent;
import com.reservation.reservation.application.dto.CreateReservationCommand;
import com.reservation.reservation.application.dto.ReservationResult;
import com.reservation.reservation.domain.exception.CurrencyMismatchException;
import com.reservation.reservation.domain.exception.InsufficientInventoryException;
import com.reservation.reservation.domain.exception.InventoryNotInitializedException;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryCount;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationStatus;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.repository.ReservationRepository;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import com.reservation.reservation.domain.service.GuestVerificationPort;
import com.reservation.reservation.domain.service.RoomTypeRateQuotePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CreateReservationApplicationService")
class CreateReservationApplicationServiceTest {

    private static final String HOTEL_ID = "01933333-1111-7aaa-9aaa-000000000001";
    private static final String ROOM_TYPE_ID = "01933333-1111-7aaa-9aaa-000000000002";
    private static final String GUEST_ID = "01933333-1111-7aaa-9aaa-000000000003";
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-06-01");
    private static final LocalDate CHECK_OUT = LocalDate.parse("2026-06-03");
    private static final Instant FIXED_NOW = Instant.parse("2026-04-23T01:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private ReservationRepository reservationRepository;
    private RoomTypeInventoryRepository inventoryRepository;
    private GuestVerificationPort guestVerificationPort;
    private RoomTypeRateQuotePort rateQuotePort;
    private OutboxEventPublisher outboxEventPublisher;
    private TransactionTemplate transactionTemplate;

    private CreateReservationApplicationService service;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        inventoryRepository = mock(RoomTypeInventoryRepository.class);
        guestVerificationPort = mock(GuestVerificationPort.class);
        rateQuotePort = mock(RoomTypeRateQuotePort.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        transactionTemplate = mock(TransactionTemplate.class);
        // TransactionTemplate 은 단위 테스트에서 트랜잭션 시뮬레이션 없이 callback 을 즉시 실행.
        // execute(...) 가 callback.doInTransaction 을 그대로 호출하도록 stub.
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        service = new CreateReservationApplicationService(
            reservationRepository,
            inventoryRepository,
            guestVerificationPort,
            rateQuotePort,
            outboxEventPublisher,
            transactionTemplate,
            FIXED_CLOCK
        );

        // 기본 stub: Reservation.save 는 입력 그대로 반환
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("정상 흐름: guest 검증 → 2일치 견적 → 2 inventory 차감 → reservation 저장 → outbox 적재")
    void happyPath() {
        givenInventoryAvailable(CHECK_IN, 5);
        givenInventoryAvailable(CHECK_IN.plusDays(1), 5);
        givenRate(CHECK_IN, 150_000L, "KRW");
        givenRate(CHECK_IN.plusDays(1), 150_000L, "KRW");

        ReservationResult result = service.create(command(2));

        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.totalAmount()).isEqualTo(300_000L);
        assertThat(result.currency()).isEqualTo("KRW");
        assertThat(result.reservationId()).isNotBlank();

        verify(guestVerificationPort).verify(any());
        verify(rateQuotePort, times(2)).quoteFor(any(), any(), any());
        verify(inventoryRepository, times(2)).save(any());

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        Reservation saved = reservationCaptor.getValue();
        assertThat(saved.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(saved.quote().total().amount()).isEqualTo(300_000L);

        ArgumentCaptor<ReservationCreatedEvent> eventCaptor =
            ArgumentCaptor.forClass(ReservationCreatedEvent.class);
        verify(outboxEventPublisher).publish(eventCaptor.capture(), eq("reservation-events"),
            eq(HOTEL_ID));
        ReservationCreatedEvent event = eventCaptor.getValue();
        assertThat(event.totalAmount()).isEqualTo(300_000L);
        assertThat(event.currency()).isEqualTo("KRW");
        assertThat(event.reservationId()).isEqualTo(saved.id().asString());
    }

    @Test
    @DisplayName("guest 검증 실패는 그대로 전파되며 inventory 조회/차감/save 는 한 번도 호출되지 않는다")
    void guestVerificationFailureShortCircuits() {
        org.mockito.Mockito.doThrow(new GuestVerificationPort.GuestNotFoundException(
                com.reservation.reservation.domain.model.GuestId.of(GUEST_ID)))
            .when(guestVerificationPort).verify(any());

        assertThatExceptionOfType(GuestVerificationPort.GuestNotFoundException.class)
            .isThrownBy(() -> service.create(command(2)));

        verify(rateQuotePort, never()).quoteFor(any(), any(), any());
        verify(inventoryRepository, never()).findByKey(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("rate 응답 통화가 N일 사이에 다르면 CurrencyMismatchException — DB 조회 진입 전 실패")
    void currencyMismatchAcrossNights() {
        givenRate(CHECK_IN, 150_000L, "KRW");
        givenRate(CHECK_IN.plusDays(1), 1_000L, "JPY");

        assertThatExceptionOfType(CurrencyMismatchException.class)
            .isThrownBy(() -> service.create(command(2)));

        verify(inventoryRepository, never()).findByKey(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("inventory row 가 없으면 InventoryNotInitializedException — 1일차 차감 후 2일차에서 실패 (트랜잭션은 Spring 이 롤백)")
    void inventoryRowMissingThrows() {
        givenRate(CHECK_IN, 150_000L, "KRW");
        givenRate(CHECK_IN.plusDays(1), 150_000L, "KRW");
        givenInventoryAvailable(CHECK_IN, 5);
        when(inventoryRepository.findByKey(new InventoryKey(
            HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID), CHECK_IN.plusDays(1))))
            .thenReturn(java.util.Optional.empty());

        assertThatExceptionOfType(InventoryNotInitializedException.class)
            .isThrownBy(() -> service.create(command(2)));

        // 사양: 1일차 inventory 차감 + save 까지는 진행되고 2일차에서 실패해야 한다.
        // 부분 커밋 자체는 실 트랜잭션 롤백이 흡수하지만, save 호출 순서가 사양이므로 명시.
        verify(inventoryRepository, times(1)).save(any());
        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("inventory available=0 이면 InsufficientInventoryException → 409 매핑 대상")
    void insufficientInventory() {
        givenRate(CHECK_IN, 150_000L, "KRW");
        givenInventoryWithExactly(CHECK_IN, 5, 0);

        // 1박짜리 명령으로 단순화
        CreateReservationCommand cmd = new CreateReservationCommand(
            HOTEL_ID, ROOM_TYPE_ID, GUEST_ID,
            CHECK_IN, CHECK_IN.plusDays(1), 2);

        assertThatExceptionOfType(InsufficientInventoryException.class)
            .isThrownBy(() -> service.create(cmd));

        verify(reservationRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("checkOut <= checkIn 은 StayPeriod 생성 단계에서 IllegalArgument — 외부 IO 진입 전 실패")
    void invalidStayPeriodFailsFast() {
        CreateReservationCommand cmd = new CreateReservationCommand(
            HOTEL_ID, ROOM_TYPE_ID, GUEST_ID,
            CHECK_IN, CHECK_IN, 2);

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> service.create(cmd));

        verify(guestVerificationPort, never()).verify(any());
        verify(rateQuotePort, never()).quoteFor(any(), any(), any());
    }

    private CreateReservationCommand command(int numberOfGuests) {
        return new CreateReservationCommand(
            HOTEL_ID, ROOM_TYPE_ID, GUEST_ID,
            CHECK_IN, CHECK_OUT, numberOfGuests);
    }

    private void givenInventoryAvailable(LocalDate date, int total) {
        givenInventoryWithExactly(date, total, total);
    }

    private void givenInventoryWithExactly(LocalDate date, int total, int available) {
        InventoryKey key = new InventoryKey(
            HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID), date);
        RoomTypeInventory inventory = RoomTypeInventory.restore(
            key, InventoryCount.of(total), InventoryCount.of(available),
            0L, FIXED_NOW, FIXED_NOW);
        when(inventoryRepository.findByKey(key)).thenReturn(java.util.Optional.of(inventory));
    }

    private void givenRate(LocalDate date, long amount, String currency) {
        when(rateQuotePort.quoteFor(
            HotelId.of(HOTEL_ID), RoomTypeId.of(ROOM_TYPE_ID), date))
            .thenReturn(Money.of(amount, currency));
    }
}

package com.reservation.reservation.application.service;

import com.reservation.common.domain.UuidV7;
import com.reservation.common.messaging.outbox.OutboxEventPublisher;
import com.reservation.contracts.event.reservation.ReservationCreatedEvent;
import com.reservation.reservation.application.dto.CreateReservationCommand;
import com.reservation.reservation.application.dto.ReservationResult;
import com.reservation.reservation.domain.exception.InventoryNotInitializedException;
import com.reservation.reservation.domain.model.BillingQuote;
import com.reservation.reservation.domain.model.GuestId;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.NumberOfGuests;
import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.model.StayPeriod;
import com.reservation.reservation.domain.repository.ReservationRepository;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import com.reservation.reservation.domain.service.GuestVerificationPort;
import com.reservation.reservation.domain.service.RoomTypeRateQuotePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 예약 생성 Use Case (FR-RSV-02).
 *
 * <p>외부 통신과 로컬 트랜잭션 경계를 명확히 분리해 트랜잭션 내 IO 시간을 최소화한다 (DB
 * 커넥션 점유 단축). {@link TransactionTemplate} 을 사용하는 이유는 같은 빈 안에서
 * {@code @Transactional} 메서드를 self-invocation 으로 호출하면 Spring AOP 프록시가
 * 우회되어 트랜잭션이 적용되지 않는 함정을 회피하기 위함.
 *
 * <ol>
 *   <li><b>트랜잭션 밖</b>:
 *     <ul>
 *       <li>{@link GuestVerificationPort#verify} — guest-service gRPC (Deadline 3s)</li>
 *       <li>{@link RoomTypeRateQuotePort#quoteFor} — rate-service gRPC N일치 N회 후
 *           {@link Money#add} 합산 → {@link BillingQuote}</li>
 *     </ul>
 *   </li>
 *   <li><b>로컬 MySQL 트랜잭션</b>:
 *     <ul>
 *       <li>{@link RoomTypeInventory#decrease} × N일 — OCC {@code @Version} 으로 동시 차감 차단</li>
 *       <li>{@link Reservation#create} → Aggregate 저장 + Event 적재</li>
 *       <li>{@link OutboxEventPublisher#publish} — Outbox row 추가 (별도 relay 가 Kafka 발행)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>다중 Aggregate update ({@link Reservation} + N {@link RoomTypeInventory}) 를 한
 * 트랜잭션에 담는 정당화는 ADR 0003 — PRD §3.2 의 SoT 요구사항(오버부킹 방지) 으로 동일
 * 트랜잭션이 불가피.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReservationApplicationService {

    private static final String TOPIC = "reservation-events";

    private final ReservationRepository reservationRepository;
    private final RoomTypeInventoryRepository inventoryRepository;
    private final GuestVerificationPort guestVerificationPort;
    private final RoomTypeRateQuotePort rateQuotePort;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ReservationResult create(CreateReservationCommand command) {
        Objects.requireNonNull(command, "command");

        HotelId hotelId = HotelId.of(command.hotelId());
        RoomTypeId roomTypeId = RoomTypeId.of(command.roomTypeId());
        GuestId guestId = GuestId.of(command.guestId());
        StayPeriod stayPeriod = new StayPeriod(command.checkInDate(), command.checkOutDate());
        NumberOfGuests numberOfGuests = NumberOfGuests.of(command.numberOfGuests());

        guestVerificationPort.verify(guestId);
        BillingQuote quote = quote(hotelId, roomTypeId, stayPeriod);

        return transactionTemplate.execute(status ->
            commitReservation(hotelId, roomTypeId, guestId, stayPeriod, numberOfGuests, quote));
    }

    private ReservationResult commitReservation(HotelId hotelId,
                                                 RoomTypeId roomTypeId,
                                                 GuestId guestId,
                                                 StayPeriod stayPeriod,
                                                 NumberOfGuests numberOfGuests,
                                                 BillingQuote quote) {
        for (LocalDate stayDate : stayPeriod.stayDates()) {
            RoomTypeInventory inventory = inventoryRepository
                .findByKey(new InventoryKey(hotelId, roomTypeId, stayDate))
                .orElseThrow(() -> new InventoryNotInitializedException(hotelId, roomTypeId, stayDate));
            inventory.decrease(clock);
            inventoryRepository.save(inventory);
        }

        Reservation reservation = Reservation.create(
            hotelId, roomTypeId, guestId, stayPeriod, numberOfGuests, quote, clock);
        Reservation saved = reservationRepository.save(reservation);

        ReservationCreatedEvent event = new ReservationCreatedEvent(
            UuidV7.create(),
            saved.createdAt(),
            saved.id().asString(),
            saved.hotelId().asString(),
            saved.roomTypeId().asString(),
            saved.guestId().asString(),
            saved.stayPeriod().checkIn(),
            saved.stayPeriod().checkOut(),
            saved.numberOfGuests().value(),
            saved.quote().total().amount(),
            saved.quote().total().currency()
        );
        outboxEventPublisher.publish(event, TOPIC, hotelId.asString());

        log.info("Reservation created reservationId={} hotelId={} roomTypeId={} guestId={}"
                + " checkIn={} checkOut={} totalAmount={} currency={}",
            saved.id().asString(), hotelId.asString(), roomTypeId.asString(), guestId.asString(),
            stayPeriod.checkIn(), stayPeriod.checkOut(),
            quote.total().amount(), quote.total().currency());

        return ReservationResult.of(saved);
    }

    private BillingQuote quote(HotelId hotelId, RoomTypeId roomTypeId, StayPeriod stayPeriod) {
        // StayPeriod 가 checkOut > checkIn 을 강제하므로 1일 이상 보장되지만, 호출 직전
        // 명시 가드로 빈 응답이 흘러 들어와 BillingQuote(null) NPE 가 나는 경로를 차단.
        java.util.List<LocalDate> dates = stayPeriod.stayDates();
        if (dates.isEmpty()) {
            throw new IllegalStateException(
                "StayPeriod yielded zero stayDates: " + stayPeriod);
        }
        Money total = null;
        for (LocalDate date : dates) {
            Money nightly = rateQuotePort.quoteFor(hotelId, roomTypeId, date);
            total = (total == null) ? nightly : total.add(nightly);
        }
        return new BillingQuote(total, Instant.now(clock));
    }
}

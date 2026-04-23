package com.reservation.reservation.application.service;

import com.reservation.common.domain.UuidV7;
import com.reservation.common.messaging.outbox.OutboxEventPublisher;
import com.reservation.contracts.event.billing.BillingCreationFailedEvent;
import com.reservation.contracts.event.reservation.ReservationCancelledEvent;
import com.reservation.reservation.application.dto.CancelReservationResult;
import com.reservation.reservation.application.idempotency.ProcessedEventStore;
import com.reservation.reservation.domain.exception.InventoryNotInitializedException;
import com.reservation.reservation.domain.exception.ReservationAlreadyCancelledException;
import com.reservation.reservation.domain.exception.ReservationNotFoundException;
import com.reservation.reservation.domain.model.CancellationPolicy;
import com.reservation.reservation.domain.model.CancellationReason;
import com.reservation.reservation.domain.model.InventoryKey;
import com.reservation.reservation.domain.model.Reservation;
import com.reservation.reservation.domain.model.ReservationId;
import com.reservation.reservation.domain.model.RoomTypeInventory;
import com.reservation.reservation.domain.repository.ReservationRepository;
import com.reservation.reservation.domain.repository.RoomTypeInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 예약 취소 Use Case (FR-RSV-03).
 *
 * <p>두 진입점:
 * <ul>
 *   <li>{@link #cancelByUser(ReservationId)} — REST API. 미존재/이미 취소는 도메인 예외를
 *       그대로 전파해 Presentation 에서 404/409 로 매핑.</li>
 *   <li>{@link #cancelOnBillingFailure(ReservationId, java.util.UUID)} — Saga 보상 경로.
 *       {@code BillingCreationFailedEvent} 수신 시 호출. 미존재/이미 취소는 noop 으로
 *       흡수 (재전송·동시성 안전). {@code processedEventId} 기반 멱등 가드 포함.</li>
 * </ul>
 *
 * <p>두 경로 모두 {@link #performCancel} 가 동일 로컬 트랜잭션에서 처리:
 * Reservation 조회 → {@link Reservation#cancel} 전이 → N 일치 {@link RoomTypeInventory#release}
 * → Outbox 적재. 다중 Aggregate update 정당화는 ADR 0003 — 사용자/보상 경로 모두
 * 같은 SoT 요구사항 (오버부킹/under-availability 방지) 으로 동등 적용 (ddd-architect L2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReservationApplicationService {

    private static final String TOPIC = "reservation-events";

    private final ReservationRepository reservationRepository;
    private final RoomTypeInventoryRepository inventoryRepository;
    private final CancellationPolicy cancellationPolicy;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ProcessedEventStore processedEventStore;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 사용자 요청에 의한 취소. 미존재 시 {@link ReservationNotFoundException}, 이미 취소된
     * 예약이면 {@link ReservationAlreadyCancelledException} 을 그대로 전파.
     */
    public CancelReservationResult cancelByUser(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "reservationId");
        return transactionTemplate.execute(status ->
            performCancel(reservationId, CancellationReason.USER_REQUEST));
    }

    /**
     * Saga 보상 경로 — {@code BillingCreationFailedEvent} 수신 시 호출.
     *
     * <p>로컬 트랜잭션 안에서 다음을 원자적으로 처리한다:
     * <ol>
     *   <li>{@link ProcessedEventStore} 멱등 가드 — 이미 처리된 eventId 면 skip</li>
     *   <li>{@link #performCancel} (Reservation.cancel + Inventory.release × N + Outbox)</li>
     *   <li>processed_events 기록</li>
     * </ol>
     *
     * <p><b>도메인 예외 흡수 정책</b> (Kafka consumer 무한 재시도 방지를 위한 의도적 분리):
     * <ul>
     *   <li><b>흡수 (warn 로그 + processed 기록 + commit)</b>:
     *     <ul>
     *       <li>{@link ReservationNotFoundException} — 이벤트 순서 역전 / 데이터 정합성 갭. 재시도해도 같은 결과.</li>
     *       <li>{@link ReservationAlreadyCancelledException} — 사용자 취소 선행 / 재전송. 재시도 무의미.</li>
     *     </ul>
     *   </li>
     *   <li><b>비-흡수 (예외 전파 → 트랜잭션 롤백 → markProcessed 미실행 → consumer retry)</b>:
     *     <ul>
     *       <li>{@link InventoryNotInitializedException} — hotel-events 도착 지연. 다음 redelivery 시 inventory row 가 생겼을 가능성이 있어 재시도 가치 있음.</li>
     *       <li>{@link com.reservation.reservation.domain.exception.InventoryReleaseExceedsCapacityException} — 데이터 정합성 시그널 (ddd-architect M2). 운영 대응 필요. 무한 retry 는 PR-4.x DLQ 도입 시 cap 처리.</li>
     *       <li>OptimisticLockingFailureException (OCC 충돌) — 짧은 재시도로 해소.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>본 PR 시점에 DLQ 가 미구현이므로 비-흡수 예외는 Spring Kafka 의 {@code DefaultErrorHandler}
     * 유한 retry 후 에러 로그로만 노출된다. 정식 DLQ 는 PR-4.x.
     */
    public void cancelOnBillingFailure(BillingCreationFailedEvent event) {
        Objects.requireNonNull(event, "event");
        ReservationId reservationId = ReservationId.of(event.reservationId());

        transactionTemplate.executeWithoutResult(status -> {
            if (processedEventStore.isAlreadyProcessed(event.eventId())) {
                log.debug("Skip already-processed BillingCreationFailedEvent eventId={}",
                    event.eventId());
                return;
            }
            try {
                performCancel(reservationId, CancellationReason.BILLING_FAILED);
                log.info("Saga compensation cancelled reservationId={} eventId={}",
                    reservationId.asString(), event.eventId());
            } catch (ReservationNotFoundException e) {
                log.warn("Saga compensation skipped — reservation not found."
                    + " reservationId={} eventId={}", reservationId.asString(), event.eventId());
            } catch (ReservationAlreadyCancelledException e) {
                log.warn("Saga compensation skipped — reservation already cancelled."
                    + " reservationId={} eventId={}", reservationId.asString(), event.eventId());
            }
            processedEventStore.markProcessed(event.eventId(),
                BillingCreationFailedEvent.class.getSimpleName(), Instant.now(clock));
        });
    }

    private CancelReservationResult performCancel(ReservationId reservationId, CancellationReason reason) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // cancel() 자체가 이미 CANCELLED 인 경우 ReservationAlreadyCancelledException 을
        // 던진다 — 사용자 경로는 그대로 전파, 보상 경로는 위에서 catch.
        reservation.cancel(cancellationPolicy, reason, clock);

        for (LocalDate stayDate : reservation.stayPeriod().stayDates()) {
            RoomTypeInventory inventory = inventoryRepository
                .findByKey(new InventoryKey(reservation.hotelId(), reservation.roomTypeId(), stayDate))
                .orElseThrow(() -> new InventoryNotInitializedException(
                    reservation.hotelId(), reservation.roomTypeId(), stayDate));
            inventory.release(clock);
            inventoryRepository.save(inventory);
        }

        Reservation saved = reservationRepository.save(reservation);
        publishCancelled(saved);

        var savedCancellation = saved.cancellation().orElseThrow();
        log.info("Reservation cancelled reservationId={} reason={} refundRate={} policy={}",
            saved.id().asString(), reason,
            savedCancellation.outcome().refundRate(),
            savedCancellation.outcome().policyName());

        return CancelReservationResult.of(saved);
    }

    private void publishCancelled(Reservation saved) {
        var cancellation = saved.cancellation().orElseThrow();
        ReservationCancelledEvent event = new ReservationCancelledEvent(
            UuidV7.create(),
            cancellation.cancelledAt(),
            saved.id().asString(),
            saved.hotelId().asString(),
            saved.roomTypeId().asString(),
            saved.stayPeriod().checkIn(),
            saved.stayPeriod().checkOut()
        );
        outboxEventPublisher.publish(event, TOPIC, saved.hotelId().asString());
    }
}

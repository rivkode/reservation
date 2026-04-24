package com.reservation.hotel.application.service;

import com.reservation.contracts.event.reservation.ReservationCancelledEvent;
import com.reservation.contracts.event.reservation.ReservationCreatedEvent;
import com.reservation.hotel.application.idempotency.ProcessedEventStore;
import com.reservation.hotel.application.port.RoomAvailabilityCache;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * reservation-events 수신 처리의 orchestrator.
 *
 * <p>FR-H-07 — hotel-service 의 Redis {@code RoomAvailabilityView} 캐시를 예약 확정/취소에
 * 따라 {@code available} 을 원자적으로 증감한다 (ADR 0004 캐시 동기화 §절).
 *
 * <p>각 public 메서드는 단일 이벤트 하나를 한 로컬 트랜잭션으로 처리한다. 트랜잭션 범위에
 * (Redis 증감 호출) + ({@code processed_events} 기록) 이 들어가지만 Redis 는 별도 connection
 * 이라 MySQL 롤백과 동기화되지 않는다 — 커밋 실패 시 Redis 만 반영된 "drift" 가 발생할 수 있고
 * 이는 PR-3.3 일일 재구축 배치가 보정한다.
 *
 * <p>at-least-once 재전송은 {@link ProcessedEventStore} 가 흡수. key 가 아직 채워지지 않은
 * 상태(→ PR-3.3 재구축 전) 에서의 이벤트는 {@code false} 반환으로 skip 되며, {@code warn}
 * 한 줄로 관측성만 확보하고 {@code markProcessed} 는 정상 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationEventApplicationService {

    /** 예약 1건 = 숙박 일자당 객실 1개 차감 — 현 PRD 범위에선 "per stay day, single room". */
    private static final int DELTA_PER_DAY_ON_RESERVED = -1;
    private static final int DELTA_PER_DAY_ON_CANCELLED = +1;

    private final RoomAvailabilityCache cache;
    private final ProcessedEventStore processedEventStore;
    private final Clock clock;

    @Transactional
    public void onReservationCreated(ReservationCreatedEvent event) {
        Objects.requireNonNull(event, "event");
        if (processedEventStore.isAlreadyProcessed(event.eventId())) {
            log.debug("Skip already-processed ReservationCreatedEvent eventId={}", event.eventId());
            return;
        }

        applyAvailabilityDelta(
            event.eventId(),
            HotelId.of(event.hotelId()),
            RoomTypeId.of(event.roomTypeId()),
            event.checkInDate(),
            event.checkOutDate(),
            DELTA_PER_DAY_ON_RESERVED);

        markProcessed(event.eventId(), ReservationCreatedEvent.class.getSimpleName());
    }

    @Transactional
    public void onReservationCancelled(ReservationCancelledEvent event) {
        Objects.requireNonNull(event, "event");
        if (processedEventStore.isAlreadyProcessed(event.eventId())) {
            log.debug("Skip already-processed ReservationCancelledEvent eventId={}", event.eventId());
            return;
        }

        applyAvailabilityDelta(
            event.eventId(),
            HotelId.of(event.hotelId()),
            RoomTypeId.of(event.roomTypeId()),
            event.checkInDate(),
            event.checkOutDate(),
            DELTA_PER_DAY_ON_CANCELLED);

        markProcessed(event.eventId(), ReservationCancelledEvent.class.getSimpleName());
    }

    /**
     * 체크인 당일부터 체크아웃 전일까지(=숙박일 반개구간 {@code [checkIn, checkOut)}) 각 날짜에
     * 대해 {@link RoomAvailabilityCache#adjustAvailable} 를 호출한다. 체크아웃 당일은 숙박에
     * 포함되지 않으므로 제외한다 (reservation-service 관례와 일치).
     *
     * <p>날짜 구간이 비어있거나 역전된 경우 (계약상 발생하지 않아야 하나 방어적으로) warn 로그
     * 후 skip 하고 {@code markProcessed} 는 호출자가 수행한다.
     *
     * <p><strong>부분 실패 격리</strong> — 루프 중간에 Redis 장애({@link DataAccessException})
     * 가 발생하면 해당 날짜만 {@code skipped} 로 취급하고 루프를 이어간다. 예외를 그대로
     * 전파하면 이미 성공한 앞선 날짜의 HINCRBY 는 Redis 단일 thread 가 커밋한 상태라 롤백
     * 불가능하고, MySQL 트랜잭션 롤백 후 Kafka 재전송으로 전체 루프가 다시 돌면 동일 날짜가
     * 두 번 증감되어 drift 가 커진다. 날짜 단위 skip + PR-3.3 재구축 배치가 보정하는 방식이
     * key 부재와 동일한 안전장치로 통일적이다. 집계된 skip 수는 한 번의 warn 로그로 보고 —
     * 이벤트당 최대 90일 루프에서 매 날짜 로그는 관측성 노이즈가 크다.
     */
    private void applyAvailabilityDelta(UUID eventId,
                                        HotelId hotelId,
                                        RoomTypeId roomTypeId,
                                        LocalDate checkInDate,
                                        LocalDate checkOutDate,
                                        int delta) {
        if (!checkOutDate.isAfter(checkInDate)) {
            log.warn("Empty or inverted stay range for eventId={}: checkIn={} checkOut={} — skipping delta apply",
                eventId, checkInDate, checkOutDate);
            return;
        }

        Instant updatedAt = Instant.now(clock);
        int applied = 0;
        int skipped = 0;
        for (LocalDate date = checkInDate; date.isBefore(checkOutDate); date = date.plusDays(1)) {
            try {
                boolean ok = cache.adjustAvailable(hotelId, roomTypeId, date, delta, updatedAt);
                if (ok) {
                    applied++;
                } else {
                    skipped++;
                }
            } catch (DataAccessException e) {
                log.warn("Redis adjustAvailable failed for date={} eventId={} — skipping, rebuild will repair",
                    date, eventId, e);
                skipped++;
            }
        }

        if (skipped > 0) {
            log.warn("Redis key absent or unreachable for {}/{} stay days while applying delta={} eventId={}"
                    + " hotelId={} roomTypeId={} — entries will be populated by PR-3.3 rebuild",
                skipped, skipped + applied, delta, eventId, hotelId.asString(), roomTypeId.asString());
        }
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventStore.markProcessed(eventId, eventType, Instant.now(clock));
    }
}

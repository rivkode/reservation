package com.reservation.rate.application.service;

import com.reservation.common.domain.UuidV7;
import com.reservation.common.messaging.outbox.OutboxEventPublisher;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * RoomTypeRate Aggregate Use Case. FR-R-01 등록 · FR-R-02 범위 조회 · FR-R-03 변경 시
 * {@link RoomTypeRateChangedEvent} 를 {@code rate-events} 토픽으로 발행.
 *
 * <p>이벤트 발행 전략 (Plan 문서 · ADR 0003 정합):
 * <ul>
 *   <li>등록(register) 시점에는 이벤트를 발행하지 않는다 — PRD FR-R-03 은 "요금
 *       <b>변경</b> 시" 발행으로 한정한다.</li>
 *   <li>{@link #change} 에서 도메인이 실제 값 변화를 감지한 경우에만
 *       ({@code RoomTypeRate#changeAmount} 가 true 를 반환) 이벤트를 Outbox 에 적재.</li>
 * </ul>
 *
 * <p>파티션 키는 {@code hotelId.asString()} 으로 고정 — Plan Q4 결정과 hotel-events
 * 전략 일관성 유지.
 */
@Service
@RequiredArgsConstructor
public class RoomTypeRateApplicationService {

    private static final String RATE_EVENTS_TOPIC = "rate-events";

    /**
     * 범위 조회에서 한 번에 스캔 가능한 최대 날짜 폭. 1년 + 1일 을 허용한다.
     * 그 이상은 400 으로 거부해 인증 없는 엔드포인트에 대한 의도치 않은 대용량 스캔 /
     * 메모리 소진을 차단한다 — 관리자 UI 가 정책 편집 시 필요로 하는 범위는 수개월 단위.
     */
    static final int MAX_RANGE_DAYS = 366;

    private final RoomTypeRateRepository repository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    @Transactional
    public RoomTypeRateResult register(RegisterRoomTypeRateCommand command) {
        Objects.requireNonNull(command, "command");
        HotelId hotelId = HotelId.of(command.hotelId());
        RoomTypeId roomTypeId = RoomTypeId.of(command.roomTypeId());
        LocalDate date = Objects.requireNonNull(command.date(), "command.date");
        Money money = Money.of(command.amount(), command.currency());

        if (repository.existsByNaturalKey(hotelId, roomTypeId, date)) {
            throw new DuplicateRateException(hotelId, roomTypeId, date);
        }

        RoomTypeRate saved = repository.save(
            RoomTypeRate.create(hotelId, roomTypeId, date, money, clock)
        );
        return RoomTypeRateResult.of(saved);
    }

    /**
     * 금액 변경. 도메인이 "실제 변경" 을 판정했을 때만 save + 이벤트 Outbox 적재를 수행한다.
     * 동일 금액 호출은 읽기 응답만 돌려주고 DB write 와 이벤트 발행을 모두 건너뛴다.
     */
    @Transactional
    public RoomTypeRateResult change(ChangeRoomTypeRateCommand command) {
        Objects.requireNonNull(command, "command");
        RateId rateId = RateId.of(command.rateId());
        RoomTypeRate rate = repository.findById(rateId)
            .orElseThrow(() -> RateNotFoundException.byId(rateId));

        Instant now = Instant.now(clock);
        boolean changed = rate.changeAmount(
            Money.of(command.amount(), command.currency()),
            Clock.fixed(now, clock.getZone())
        );
        if (!changed) {
            return RoomTypeRateResult.of(rate);
        }

        RoomTypeRate saved = repository.save(rate);
        outboxEventPublisher.publish(
            new RoomTypeRateChangedEvent(
                UuidV7.create(),
                now,
                saved.hotelId().asString(),
                saved.roomTypeId().asString(),
                saved.date(),
                saved.money().amount(),
                saved.money().currencyCode()
            ),
            RATE_EVENTS_TOPIC,
            saved.hotelId().asString()
        );
        return RoomTypeRateResult.of(saved);
    }

    @Transactional(readOnly = true)
    public RoomTypeRateResult findById(String rateId) {
        Objects.requireNonNull(rateId, "rateId");
        RateId id = RateId.of(rateId);
        return RoomTypeRateResult.of(repository.findById(id)
            .orElseThrow(() -> RateNotFoundException.byId(id)));
    }

    /**
     * FR-R-02 — 특정 호텔 · 객실 타입 · 날짜 범위의 요금 조회. {@code from > to} 는
     * 호출자 실수로 즉시 거부 (400 으로 매핑).
     */
    @Transactional(readOnly = true)
    public List<RoomTypeRateResult> findByRange(String hotelId, String roomTypeId,
                                                LocalDate from, LocalDate to) {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be <= to, from=" + from + " to=" + to);
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(
                "range too wide (max " + MAX_RANGE_DAYS + " days), was " + days + " days");
        }

        return repository.findByRange(HotelId.of(hotelId), RoomTypeId.of(roomTypeId), from, to)
            .stream()
            .map(RoomTypeRateResult::of)
            .toList();
    }
}

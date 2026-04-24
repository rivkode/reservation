package com.reservation.hotel.application.service;

import com.reservation.hotel.application.dto.AvailabilityQuery;
import com.reservation.hotel.application.dto.AvailabilityResult;
import com.reservation.hotel.application.dto.DailyAvailability;
import com.reservation.hotel.application.dto.RoomAvailabilitySnapshot;
import com.reservation.hotel.application.port.RoomAvailabilityQuery;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * FR-H-06 — 가용성 조회 Application Service.
 *
 * <p>ADR 0004 의 "조회는 Redis 만" 정책에 따라 {@link RoomAvailabilityQuery} 포트만 호출하고
 * reservation-service gRPC fallback 은 수행하지 않는다. value 부재 날짜는 응답에서 제외
 * 되며, PR-3.3 재구축 배치가 보정될 때까지 클라이언트가 날짜 필드로 누락을 감지한다.
 *
 * <p>{@code staleUntil} 계산 — 응답에 포함된 모든 스냅샷의 가장 오래된 {@code updatedAt}
 * 에 ADR 0004 / PRD §6 의 허용 stale 기간({@code STALE_DURATION_SECONDS}) 을 더한 값.
 * 조회 결과가 비어있으면 {@code null}.
 */
@Slf4j
@Service
public class AvailabilityQueryApplicationService {

    /** PRD §6 — 캐시 stale 허용 시간 (이벤트 처리 지연 포함). */
    private static final Duration STALE_DURATION = Duration.ofSeconds(30);

    private final RoomAvailabilityQuery availabilityQuery;
    private final int maxRangeDays;

    public AvailabilityQueryApplicationService(RoomAvailabilityQuery availabilityQuery,
                                               @Value("${app.availability.max-range-days:90}") int maxRangeDays) {
        this.availabilityQuery = availabilityQuery;
        if (maxRangeDays <= 0) {
            throw new IllegalArgumentException(
                "app.availability.max-range-days must be positive, was " + maxRangeDays);
        }
        this.maxRangeDays = maxRangeDays;
    }

    /**
     * Redis 만 조회하므로 {@code @Transactional} 을 붙이지 않는다 — JPA 트랜잭션을 열면 DB
     * 커넥션 풀을 낭비하고 조회 API 의 지연에 기여한다.
     */
    public AvailabilityResult query(AvailabilityQuery query) {
        Objects.requireNonNull(query, "query");
        validateRange(query);

        HotelId hotelId = HotelId.of(query.hotelId());
        RoomTypeId roomTypeId = RoomTypeId.of(query.roomTypeId());

        List<RoomAvailabilitySnapshot> snapshots = availabilityQuery.readRange(
            hotelId, roomTypeId, query.checkIn(), query.checkOut());

        List<DailyAvailability> daily = snapshots.stream()
            .map(s -> new DailyAvailability(s.date(), s.available(), s.total()))
            .toList();

        Instant staleUntil = computeStaleUntil(snapshots);

        return new AvailabilityResult(query.hotelId(), query.roomTypeId(), daily, staleUntil);
    }

    private void validateRange(AvailabilityQuery query) {
        if (!query.checkOut().isAfter(query.checkIn())) {
            throw new IllegalArgumentException(
                "checkOut must be after checkIn: checkIn=" + query.checkIn() + " checkOut=" + query.checkOut());
        }
        long days = ChronoUnit.DAYS.between(query.checkIn(), query.checkOut());
        if (days > maxRangeDays) {
            throw new IllegalArgumentException(
                "Requested range " + days + " days exceeds max " + maxRangeDays);
        }
    }

    private Instant computeStaleUntil(List<RoomAvailabilitySnapshot> snapshots) {
        Optional<Instant> oldest = snapshots.stream()
            .map(RoomAvailabilitySnapshot::updatedAt)
            .min(Comparator.naturalOrder());
        return oldest.map(i -> i.plus(STALE_DURATION)).orElse(null);
    }
}

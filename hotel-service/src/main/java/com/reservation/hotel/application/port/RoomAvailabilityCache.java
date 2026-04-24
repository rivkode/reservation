package com.reservation.hotel.application.port;

import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code RoomAvailabilityView} Read Model (ADR 0004) 에 대한 Application 포트.
 *
 * <p>구현체는 {@code infrastructure/cache/} 에 위치하며 Redis Hash 로 아래 스키마를 유지한다.
 * <pre>
 * key:    avail:{hotelId}:{roomTypeId}:{YYYY-MM-DD}
 * fields: available (int), total (int), updatedAt (ISO-8601 string)
 * </pre>
 *
 * <p>key 는 hotel-service 로컬에서 논리적으로 계산 가능하지만 정확한 {@code available}
 * 값은 reservation-service SoT 에서만 얻을 수 있다 (PR-3.3 {@code StreamInventory}
 * gRPC 재구축 책임). 따라서 본 포트는 "이미 존재하는 key 에 대한 원자적 증감" 에만
 * 집중하며, key 부재 상황은 {@code false} 반환으로 호출자에게 위임한다.
 */
public interface RoomAvailabilityCache {

    /**
     * {@code available} 필드를 {@code delta} 만큼 원자적으로 증감하고 {@code updatedAt}
     * 을 갱신한다. 음수 delta 는 예약 생성, 양수 delta 는 예약 취소를 표현.
     *
     * <p>key 가 존재하지 않으면 아무 변경도 하지 않고 {@code false} 를 반환한다 —
     * {@code HINCRBY} 가 key 를 자동 생성하며 {@code total} 을 알 수 없는 상태로
     * 음수 {@code available} 을 만드는 것을 방지한다. 호출자는 {@code processed_events}
     * 기록만 남기고 스킵한다.
     *
     * @param hotelId     호텔 식별자
     * @param roomTypeId  객실 타입 식별자
     * @param stayDate    투숙 일자 (체크인 ~ 체크아웃-1 반개구간 중 하루)
     * @param delta       증감값 — 보통 {@code -1} (생성) 또는 {@code +1} (취소)
     * @param updatedAt   캐시 갱신 시각 (ISO-8601 으로 저장)
     * @return key 가 이미 존재해 증감에 성공하면 {@code true}, 부재로 skip 했으면 {@code false}
     */
    boolean adjustAvailable(HotelId hotelId,
                            RoomTypeId roomTypeId,
                            LocalDate stayDate,
                            int delta,
                            Instant updatedAt);
}

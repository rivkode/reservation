package com.reservation.reservation.domain.service;

import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.RoomTypeId;

import java.time.LocalDate;

/**
 * 예약 생성 시 1박 요금을 외부 SoT(rate-service) 에 견적하는 도메인 포트.
 *
 * <p>Application Service 는 {@code [checkIn, checkOut)} 범위의 N 일치를 본 포트로
 * N 회 호출하고 {@code Money.add} 로 합산한다 — rate.proto 의 단일 RPC 단위와 일치.
 * 본 PR 은 totalAmount 견적 용도에만 사용되며 결제·정산은 하지 않는다 (PRD §3.2 Q1).
 *
 * <p>구현 책임:
 * <ul>
 *   <li>모든 호출에 Deadline 을 설정해야 한다 (PR-2.2 는 3 초 고정)</li>
 *   <li>rate 가 존재하지 않으면 {@link RoomTypeRateNotFoundException}</li>
 *   <li>통신 실패는 {@link RateServiceUnavailableException}</li>
 * </ul>
 */
public interface RoomTypeRateQuotePort {

    /** 1박치 요금 조회. 응답 통화는 호출자가 합산 시 일관성 검증 (Money.add). */
    Money quoteFor(HotelId hotelId, RoomTypeId roomTypeId, LocalDate stayDate);

    /** 외부 SoT 에 해당 호텔·객실타입·날짜의 rate 가 등록되지 않음 (404 매핑). */
    class RoomTypeRateNotFoundException extends RuntimeException {
        public RoomTypeRateNotFoundException(HotelId hotelId, RoomTypeId roomTypeId, LocalDate date) {
            super("Rate not found for hotel=" + hotelId.asString()
                + ", roomType=" + roomTypeId.asString() + ", date=" + date);
        }
    }

    /** rate-service 통신 실패 (503 매핑). */
    class RateServiceUnavailableException extends RuntimeException {
        public RateServiceUnavailableException(HotelId hotelId, RoomTypeId roomTypeId,
                                               LocalDate date, Throwable cause) {
            super("Rate service unavailable for hotel=" + hotelId.asString()
                + ", roomType=" + roomTypeId.asString() + ", date=" + date, cause);
        }
    }
}

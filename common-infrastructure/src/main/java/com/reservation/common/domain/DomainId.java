package com.reservation.common.domain;

import java.util.UUID;

/**
 * 서비스별 Aggregate 식별자 record 들이 구현하는 마커 interface.
 *
 * <p>각 서비스는 자체 구체 ID 를 선언한다. 예:
 * <pre>
 * public record HotelId(UUID value) implements DomainId {
 *     public static HotelId newId() { return new HotelId(UuidV7.create()); }
 * }
 * </pre>
 *
 * <p>저장소 레이어는 {@link com.reservation.common.persistence.UuidBinaryConverter}
 * 로 UUID ↔ BINARY(16) 변환을 수행한다. 이 계약을 통해 서비스별 ID 타입이 달라도
 * 공용 유틸(직렬화 · 로깅 MDC · gRPC 메타데이터 전파) 이 동일 인터페이스로 접근 가능하다.
 */
public interface DomainId {

    UUID value();
}

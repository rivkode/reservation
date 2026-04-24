package com.reservation.hotel.application.port;

import com.reservation.hotel.application.dto.InventoryRebuildEntry;
import com.reservation.hotel.domain.model.HotelId;

import java.time.LocalDate;
import java.util.List;

/**
 * 재구축 배치가 reservation-service SoT 에서 Inventory 스냅샷을 받아오기 위한 Application
 * 포트. 구현체는 {@code reservation.proto/StreamInventory} gRPC 를 호출한다.
 *
 * <p>포트로 분리해 두면 단위 테스트에서 실제 gRPC 없이도 orchestrator 를 검증할 수 있고,
 * 향후 통신 방식이 변경되어도({@code StreamInventory} 를 REST 나 Kafka 로 바꾸는 가정)
 * Application 레이어 변경 없이 교체 가능하다.
 */
public interface InventorySnapshotSource {

    /**
     * 주어진 호텔의 {@code [from, to]} 범위 (양 끝 포함 — gRPC contract 에 맞춰) 스냅샷을
     * 전부 반환한다. 존재하지 않는 호텔은 빈 리스트. 범위는 최대 90일로 배치가 조절한다.
     *
     * @throws InventoryStreamUnavailableException 원격 호출 실패
     */
    List<InventoryRebuildEntry> findByHotelInRange(HotelId hotelId,
                                                   LocalDate fromInclusive,
                                                   LocalDate toInclusive);

    /** 원격 호출 실패를 표현하는 비검사 예외. 호출자는 해당 호텔만 skip + 로그. */
    class InventoryStreamUnavailableException extends RuntimeException {
        public InventoryStreamUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

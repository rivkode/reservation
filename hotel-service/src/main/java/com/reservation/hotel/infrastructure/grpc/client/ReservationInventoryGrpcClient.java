package com.reservation.hotel.infrastructure.grpc.client;

import com.reservation.contracts.reservation.ReservationInventoryServiceGrpc;
import com.reservation.contracts.reservation.RoomTypeInventorySnapshot;
import com.reservation.contracts.reservation.StreamInventoryRequest;
import com.reservation.hotel.application.dto.InventoryRebuildEntry;
import com.reservation.hotel.application.port.InventorySnapshotSource;
import com.reservation.hotel.domain.model.HotelId;
import com.reservation.hotel.domain.model.RoomTypeId;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link InventorySnapshotSource} 의 gRPC 어댑터.
 *
 * <p>reservation-service 의 {@code ReservationInventoryService.StreamInventory} 를 호출해
 * server-streaming 응답을 iterator 로 순회하며 {@link InventoryRebuildEntry} 로 변환한다.
 * {@code updatedAt} 은 proto 스키마에 없으므로 수신 시각({@code Instant.now(clock)}) 을 대입
 * — "이 entry 를 SoT 로부터 마지막으로 읽어온 시각" 의미로 staleUntil 계산에 활용.
 *
 * <p>전체 스트림 Deadline 을 30 초로 둔다 (90일 × 다수 room type × 다수 호텔이 아닌 한
 * 호텔 건으로는 충분한 여유). Circuit Breaker 는 PR-4.1 에서 일괄 도입.
 */
@Component
@Slf4j
public class ReservationInventoryGrpcClient implements InventorySnapshotSource {

    static final long STREAM_DEADLINE_MS = 30_000L;

    private final ReservationInventoryServiceGrpc.ReservationInventoryServiceBlockingStub stub;
    private final Clock clock;

    public ReservationInventoryGrpcClient(
        @GrpcClient("reservation-service") ReservationInventoryServiceGrpc.ReservationInventoryServiceBlockingStub stub,
        Clock clock) {
        this.stub = stub;
        this.clock = clock;
    }

    @Override
    public List<InventoryRebuildEntry> findByHotelInRange(HotelId hotelId,
                                                          LocalDate fromInclusive,
                                                          LocalDate toInclusive) {
        StreamInventoryRequest request = StreamInventoryRequest.newBuilder()
            .setHotelId(hotelId.asString())
            .setFromDate(fromInclusive.toString())
            .setToDate(toInclusive.toString())
            .build();

        Instant receivedAt = Instant.now(clock);
        List<InventoryRebuildEntry> entries = new ArrayList<>();
        try {
            Iterator<RoomTypeInventorySnapshot> iterator = stub
                .withDeadlineAfter(STREAM_DEADLINE_MS, TimeUnit.MILLISECONDS)
                .streamInventory(request);
            while (iterator.hasNext()) {
                RoomTypeInventorySnapshot proto = iterator.next();
                entries.add(new InventoryRebuildEntry(
                    HotelId.of(proto.getHotelId()),
                    RoomTypeId.of(proto.getRoomTypeId()),
                    LocalDate.parse(proto.getDate()),
                    proto.getAvailable(),
                    proto.getTotalInventory(),
                    receivedAt));
            }
        } catch (StatusRuntimeException e) {
            log.warn("StreamInventory gRPC failed hotelId={} status={}",
                hotelId.asString(), e.getStatus().getCode());
            throw new InventoryStreamUnavailableException(
                "StreamInventory failed for hotelId=" + hotelId.asString(), e);
        }
        return entries;
    }
}

package com.reservation.hotel.infrastructure.grpc.client;

import com.reservation.contracts.reservation.ReservationInventoryServiceGrpc;
import com.reservation.contracts.reservation.RoomTypeInventorySnapshot;
import com.reservation.contracts.reservation.StreamInventoryRequest;
import com.reservation.hotel.application.dto.InventoryRebuildEntry;
import com.reservation.hotel.application.port.InventorySnapshotSource;
import com.reservation.hotel.domain.model.HotelId;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * gRPC in-process 서버를 띄워 {@link ReservationInventoryGrpcClient} 의 응답 매핑과 오류
 * 매핑을 검증. 실제 네트워크 없이 {@code Status.Code} → {@link InventorySnapshotSource.InventoryStreamUnavailableException}
 * 변환 계약을 고정한다.
 */
@DisplayName("ReservationInventoryGrpcClient — in-process gRPC")
class ReservationInventoryGrpcClientTest {

    private static final HotelId HOTEL_ID =
        HotelId.of(UUID.fromString("01933333-1111-7aaa-9aaa-111122223333"));
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination();
        }
    }

    @Test
    @DisplayName("정상 stream — proto 수신 후 updatedAt=수신시각 으로 entry 변환")
    void converts_stream_to_entries() throws Exception {
        RoomTypeInventorySnapshot row1 = RoomTypeInventorySnapshot.newBuilder()
            .setHotelId(HOTEL_ID.asString())
            .setRoomTypeId("01933333-2222-7aaa-9aaa-111122223333")
            .setDate("2026-06-01")
            .setTotalInventory(10)
            .setAvailable(3)
            .build();
        RoomTypeInventorySnapshot row2 = row1.toBuilder()
            .setDate("2026-06-02").setAvailable(5).build();
        startServer(new ReservationInventoryServiceGrpc.ReservationInventoryServiceImplBase() {
            @Override
            public void streamInventory(StreamInventoryRequest request,
                                        StreamObserver<RoomTypeInventorySnapshot> obs) {
                obs.onNext(row1);
                obs.onNext(row2);
                obs.onCompleted();
            }
        });

        ReservationInventoryGrpcClient client = newClient();
        List<InventoryRebuildEntry> result = client.findByHotelInRange(
            HOTEL_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.get(0).available()).isEqualTo(3);
        assertThat(result.get(0).total()).isEqualTo(10);
        assertThat(result.get(0).updatedAt()).isEqualTo(NOW);
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 6, 2));
        assertThat(result.get(1).available()).isEqualTo(5);
    }

    @Test
    @DisplayName("UNAVAILABLE → InventoryStreamUnavailableException")
    void maps_unavailable_to_domain_exception() throws Exception {
        startServer(new ReservationInventoryServiceGrpc.ReservationInventoryServiceImplBase() {
            @Override
            public void streamInventory(StreamInventoryRequest request,
                                        StreamObserver<RoomTypeInventorySnapshot> obs) {
                obs.onError(Status.UNAVAILABLE.withDescription("down").asRuntimeException());
            }
        });

        ReservationInventoryGrpcClient client = newClient();

        assertThatExceptionOfType(InventorySnapshotSource.InventoryStreamUnavailableException.class)
            .isThrownBy(() -> client.findByHotelInRange(
                HOTEL_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    @DisplayName("INTERNAL → InventoryStreamUnavailableException (본 클라이언트는 하위 에러를 일괄 변환)")
    void maps_internal_to_domain_exception() throws Exception {
        startServer(new ReservationInventoryServiceGrpc.ReservationInventoryServiceImplBase() {
            @Override
            public void streamInventory(StreamInventoryRequest request,
                                        StreamObserver<RoomTypeInventorySnapshot> obs) {
                obs.onError(Status.INTERNAL.withDescription("boom").asRuntimeException());
            }
        });

        ReservationInventoryGrpcClient client = newClient();

        assertThatExceptionOfType(InventorySnapshotSource.InventoryStreamUnavailableException.class)
            .isThrownBy(() -> client.findByHotelInRange(
                HOTEL_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));
    }

    private void startServer(ReservationInventoryServiceGrpc.ReservationInventoryServiceImplBase service)
        throws Exception {
        String name = "test-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(service)
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    private ReservationInventoryGrpcClient newClient() {
        ReservationInventoryServiceGrpc.ReservationInventoryServiceBlockingStub stub =
            ReservationInventoryServiceGrpc.newBlockingStub(channel);
        return new ReservationInventoryGrpcClient(stub, CLOCK);
    }
}

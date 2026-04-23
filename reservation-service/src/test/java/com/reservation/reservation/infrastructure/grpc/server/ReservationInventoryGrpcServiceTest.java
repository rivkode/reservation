package com.reservation.reservation.infrastructure.grpc.server;

import com.reservation.contracts.reservation.ReservationInventoryServiceGrpc;
import com.reservation.contracts.reservation.RoomTypeInventorySnapshot;
import com.reservation.contracts.reservation.StreamInventoryRequest;
import com.reservation.reservation.application.dto.InventorySnapshotResult;
import com.reservation.reservation.application.service.InventoryQueryApplicationService;
import com.reservation.reservation.domain.model.HotelId;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * gRPC 서버 단위 테스트. Spring 컨텍스트 없이 in-process transport 로 실제 stub 호출
 * 경로를 검증한다 ({@link InventoryQueryApplicationService} 는 Mockito mock).
 *
 * <p>검증 대상:
 * <ul>
 *   <li>정상 경로: Application DTO 리스트 → proto 스트림 순차 전송 + onCompleted</li>
 *   <li>빈 결과: onNext 없이 onCompleted (에러 아님)</li>
 *   <li>잘못된 UUID / 날짜 / 역전 범위 / 범위 상한 초과 → INVALID_ARGUMENT</li>
 *   <li>Application 내부 RuntimeException → INTERNAL</li>
 * </ul>
 */
class ReservationInventoryGrpcServiceTest {

    private static final String HOTEL_UUID = "01933333-1111-7aaa-9aaa-111122223333";
    private static final String ROOM_TYPE_UUID = "01933333-aaaa-7aaa-9aaa-111122223333";
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 3);

    private InventoryQueryApplicationService applicationService;
    private Server server;
    private ManagedChannel channel;
    private ReservationInventoryServiceGrpc.ReservationInventoryServiceBlockingStub stub;

    @BeforeEach
    void startInProcessServer() throws IOException {
        applicationService = mock(InventoryQueryApplicationService.class);
        String serverName = InProcessServerBuilder.generateName();

        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new ReservationInventoryGrpcService(applicationService))
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .build();
        stub = ReservationInventoryServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void shutdown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    @DisplayName("StreamInventory: 정상 경로 — Application 결과를 proto 로 스트리밍")
    void 정상_스트리밍() {
        when(applicationService.findByHotelInRange(HotelId.of(HOTEL_UUID), FROM, TO))
            .thenReturn(List.of(
                new InventorySnapshotResult(HOTEL_UUID, ROOM_TYPE_UUID, FROM, 10, 7),
                new InventorySnapshotResult(HOTEL_UUID, ROOM_TYPE_UUID, TO, 10, 10)
            ));

        Iterator<RoomTypeInventorySnapshot> it = stub.streamInventory(requestOf(HOTEL_UUID, FROM, TO));

        RoomTypeInventorySnapshot first = it.next();
        assertThat(first.getHotelId()).isEqualTo(HOTEL_UUID);
        assertThat(first.getRoomTypeId()).isEqualTo(ROOM_TYPE_UUID);
        assertThat(first.getDate()).isEqualTo("2026-06-01");
        assertThat(first.getTotalInventory()).isEqualTo(10);
        assertThat(first.getAvailable()).isEqualTo(7);

        RoomTypeInventorySnapshot second = it.next();
        assertThat(second.getDate()).isEqualTo("2026-06-03");
        assertThat(second.getAvailable()).isEqualTo(10);

        assertThat(it.hasNext()).isFalse();
    }

    @Test
    @DisplayName("StreamInventory: 빈 결과도 에러 없이 onCompleted")
    void 빈_결과도_에러_아님() {
        when(applicationService.findByHotelInRange(HotelId.of(HOTEL_UUID), FROM, TO))
            .thenReturn(List.of());

        Iterator<RoomTypeInventorySnapshot> it = stub.streamInventory(requestOf(HOTEL_UUID, FROM, TO));

        assertThat(it.hasNext()).isFalse();
    }

    @Test
    @DisplayName("StreamInventory: 잘못된 UUID → INVALID_ARGUMENT, Application 호출 없음")
    void 잘못된_UUID() {
        StreamInventoryRequest request = StreamInventoryRequest.newBuilder()
            .setHotelId("not-a-uuid")
            .setFromDate(FROM.toString())
            .setToDate(TO.toString())
            .build();

        assertThatThrownBy(() -> drain(stub.streamInventory(request)))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("StreamInventory: 잘못된 날짜 형식 → INVALID_ARGUMENT")
    void 잘못된_날짜_형식() {
        StreamInventoryRequest request = StreamInventoryRequest.newBuilder()
            .setHotelId(HOTEL_UUID)
            .setFromDate("2026/06/01")
            .setToDate("2026-06-03")
            .build();

        assertThatThrownBy(() -> drain(stub.streamInventory(request)))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("StreamInventory: from > to → INVALID_ARGUMENT (Application 이 IAE 로 거부)")
    void 역전된_날짜() {
        when(applicationService.findByHotelInRange(any(), any(), any()))
            .thenThrow(new IllegalArgumentException("from must not be after to: from=..., to=..."));

        assertThatThrownBy(() -> drain(stub.streamInventory(requestOf(HOTEL_UUID, TO, FROM))))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        // UUID/날짜 파싱을 통과한 요청이 실제로 Application 까지 도달했음을 확인 —
        // gRPC 서버가 파싱 예외와 Application 예외를 동일 Status 로 뭉개지 않는지 검증.
        verify(applicationService).findByHotelInRange(any(), any(), any());
    }

    @Test
    @DisplayName("StreamInventory: Application 내부 RuntimeException → INTERNAL (원본 메시지 비노출)")
    void 내부예외() {
        when(applicationService.findByHotelInRange(any(), any(), any()))
            .thenThrow(new RuntimeException("boom — sensitive internal"));

        assertThatThrownBy(() -> drain(stub.streamInventory(requestOf(HOTEL_UUID, FROM, TO))))
            .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                // 내부 메시지가 그대로 유출되지 않도록 통제
                assertThat(e.getStatus().getDescription()).doesNotContain("sensitive");
            });
        verify(applicationService).findByHotelInRange(any(), any(), any());
    }

    private static StreamInventoryRequest requestOf(String hotelId, LocalDate from, LocalDate to) {
        return StreamInventoryRequest.newBuilder()
            .setHotelId(hotelId)
            .setFromDate(from.toString())
            .setToDate(to.toString())
            .build();
    }

    private static void drain(Iterator<?> it) {
        while (it.hasNext()) {
            it.next();
        }
    }
}

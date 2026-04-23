package com.reservation.reservation.infrastructure.grpc.client;

import com.reservation.contracts.rate.GetRoomTypeRateRequest;
import com.reservation.contracts.rate.RateServiceGrpc;
import com.reservation.contracts.rate.RoomTypeRate;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.service.RoomTypeRateQuotePort;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("RateServiceGrpcClient (in-process)")
class RateServiceGrpcClientTest {

    private static final HotelId HOTEL = HotelId.of("01933333-1111-7aaa-9aaa-000000000001");
    private static final RoomTypeId ROOM_TYPE = RoomTypeId.of("01933333-1111-7aaa-9aaa-000000000002");
    private static final LocalDate DATE = LocalDate.parse("2026-06-01");

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("정상 응답은 Money(amount, currency) 로 매핑")
    void quoteOk() throws IOException {
        startServer(new RateServiceGrpc.RateServiceImplBase() {
            @Override
            public void getRoomTypeRate(GetRoomTypeRateRequest request,
                                         StreamObserver<RoomTypeRate> responseObserver) {
                responseObserver.onNext(RoomTypeRate.newBuilder()
                    .setHotelId(request.getHotelId())
                    .setRoomTypeId(request.getRoomTypeId())
                    .setDate(request.getDate())
                    .setAmount(150_000L)
                    .setCurrency("KRW")
                    .build());
                responseObserver.onCompleted();
            }
        });
        RateServiceGrpcClient client = new RateServiceGrpcClient(
            RateServiceGrpc.newBlockingStub(channel));

        Money result = client.quoteFor(HOTEL, ROOM_TYPE, DATE);

        assertThat(result).isEqualTo(Money.of(150_000L, "KRW"));
    }

    @Test
    @DisplayName("NOT_FOUND 는 RoomTypeRateNotFoundException")
    void notFoundMapped() throws IOException {
        startServer(new RateServiceGrpc.RateServiceImplBase() {
            @Override
            public void getRoomTypeRate(GetRoomTypeRateRequest request,
                                         StreamObserver<RoomTypeRate> responseObserver) {
                responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
            }
        });
        RateServiceGrpcClient client = new RateServiceGrpcClient(
            RateServiceGrpc.newBlockingStub(channel));

        assertThatExceptionOfType(RoomTypeRateQuotePort.RoomTypeRateNotFoundException.class)
            .isThrownBy(() -> client.quoteFor(HOTEL, ROOM_TYPE, DATE))
            .withMessageContaining(DATE.toString());
    }

    @Test
    @DisplayName("UNAVAILABLE 응답은 RateServiceUnavailableException")
    void unavailableMapped() throws IOException {
        startServer(new RateServiceGrpc.RateServiceImplBase() {
            @Override
            public void getRoomTypeRate(GetRoomTypeRateRequest request,
                                         StreamObserver<RoomTypeRate> responseObserver) {
                responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        });
        RateServiceGrpcClient client = new RateServiceGrpcClient(
            RateServiceGrpc.newBlockingStub(channel));

        assertThatExceptionOfType(RoomTypeRateQuotePort.RateServiceUnavailableException.class)
            .isThrownBy(() -> client.quoteFor(HOTEL, ROOM_TYPE, DATE));
    }

    @Test
    @DisplayName("INTERNAL 등 그 외 오류도 RateServiceUnavailableException 으로 통일")
    void internalMappedToUnavailable() throws IOException {
        startServer(new RateServiceGrpc.RateServiceImplBase() {
            @Override
            public void getRoomTypeRate(GetRoomTypeRateRequest request,
                                         StreamObserver<RoomTypeRate> responseObserver) {
                responseObserver.onError(Status.INTERNAL.asRuntimeException());
            }
        });
        RateServiceGrpcClient client = new RateServiceGrpcClient(
            RateServiceGrpc.newBlockingStub(channel));

        assertThatExceptionOfType(RoomTypeRateQuotePort.RateServiceUnavailableException.class)
            .isThrownBy(() -> client.quoteFor(HOTEL, ROOM_TYPE, DATE));
    }

    private void startServer(RateServiceGrpc.RateServiceImplBase impl) throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).addService(impl).directExecutor().build();
        server.start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }
}

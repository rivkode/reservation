package com.reservation.reservation.infrastructure.grpc.client;

import com.reservation.contracts.guest.GetGuestRequest;
import com.reservation.contracts.guest.Guest;
import com.reservation.contracts.guest.GuestServiceGrpc;
import com.reservation.reservation.domain.model.GuestId;
import com.reservation.reservation.domain.service.GuestVerificationPort;
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

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("GuestServiceGrpcClient (in-process)")
class GuestServiceGrpcClientTest {

    private static final GuestId GUEST_ID =
        GuestId.of(UUID.fromString("01933333-1111-7aaa-9aaa-000000000003"));

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
    @DisplayName("guest 가 존재하면 verify 가 정상 반환")
    void verifyOk() throws IOException {
        startServer(new GuestServiceGrpc.GuestServiceImplBase() {
            @Override
            public void getGuest(GetGuestRequest request, StreamObserver<Guest> responseObserver) {
                responseObserver.onNext(Guest.newBuilder()
                    .setId(request.getId())
                    .setFirstName("Jane")
                    .setLastName("Doe")
                    .setEmail("jane@example.com")
                    .setPhoneNumber("+82-10-0000-0000")
                    .build());
                responseObserver.onCompleted();
            }
        });

        GuestServiceGrpcClient client = new GuestServiceGrpcClient(blockingStub());

        assertThatCode(() -> client.verify(GUEST_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NOT_FOUND 응답은 GuestNotFoundException")
    void notFoundMapped() throws IOException {
        startServer(new GuestServiceGrpc.GuestServiceImplBase() {
            @Override
            public void getGuest(GetGuestRequest request, StreamObserver<Guest> responseObserver) {
                responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
            }
        });
        GuestServiceGrpcClient client = new GuestServiceGrpcClient(blockingStub());

        assertThatExceptionOfType(GuestVerificationPort.GuestNotFoundException.class)
            .isThrownBy(() -> client.verify(GUEST_ID))
            .withMessageContaining(GUEST_ID.asString());
    }

    @Test
    @DisplayName("UNAVAILABLE 응답은 GuestVerificationUnavailableException")
    void unavailableMapped() throws IOException {
        startServer(new GuestServiceGrpc.GuestServiceImplBase() {
            @Override
            public void getGuest(GetGuestRequest request, StreamObserver<Guest> responseObserver) {
                responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        });
        GuestServiceGrpcClient client = new GuestServiceGrpcClient(blockingStub());

        assertThatExceptionOfType(GuestVerificationPort.GuestVerificationUnavailableException.class)
            .isThrownBy(() -> client.verify(GUEST_ID));
    }

    @Test
    @DisplayName("INTERNAL 등 그 외 오류도 GuestVerificationUnavailableException 으로 통일")
    void otherErrorMappedToUnavailable() throws IOException {
        startServer(new GuestServiceGrpc.GuestServiceImplBase() {
            @Override
            public void getGuest(GetGuestRequest request, StreamObserver<Guest> responseObserver) {
                responseObserver.onError(Status.INTERNAL.asRuntimeException());
            }
        });
        GuestServiceGrpcClient client = new GuestServiceGrpcClient(blockingStub());

        assertThatExceptionOfType(GuestVerificationPort.GuestVerificationUnavailableException.class)
            .isThrownBy(() -> client.verify(GUEST_ID));
    }

    private void startServer(GuestServiceGrpc.GuestServiceImplBase impl) throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).addService(impl).directExecutor().build();
        server.start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    private GuestServiceGrpc.GuestServiceBlockingStub blockingStub() {
        return GuestServiceGrpc.newBlockingStub(channel);
    }
}

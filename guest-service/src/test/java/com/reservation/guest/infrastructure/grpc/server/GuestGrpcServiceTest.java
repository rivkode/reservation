package com.reservation.guest.infrastructure.grpc.server;

import com.reservation.contracts.guest.BatchGetGuestsRequest;
import com.reservation.contracts.guest.BatchGetGuestsResponse;
import com.reservation.contracts.guest.GetGuestRequest;
import com.reservation.contracts.guest.Guest;
import com.reservation.contracts.guest.GuestServiceGrpc;
import com.reservation.guest.application.dto.GuestResult;
import com.reservation.guest.application.service.GuestApplicationService;
import com.reservation.guest.domain.exception.GuestNotFoundException;
import com.reservation.guest.domain.model.GuestId;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * gRPC 서버 단위 테스트. Spring 컨텍스트 없이 in-process transport 로 실제 stub 호출
 * 경로를 검증한다 (GuestApplicationService 는 Mockito mock).
 *
 * <p>검증 대상:
 * <ul>
 *   <li>정상 경로 (GetGuest / BatchGetGuests) 가 proto 응답을 제대로 빌드</li>
 *   <li>Application 예외 → gRPC Status 매핑</li>
 *   <li>BatchGetGuests 의 크기 상한 방어 ({@link GuestGrpcService#MAX_BATCH_SIZE})</li>
 * </ul>
 */
class GuestGrpcServiceTest {

    private static final String GUEST_ID = UUID.fromString("01970000-0000-7000-8000-000000000001").toString();

    private GuestApplicationService applicationService;
    private Server server;
    private ManagedChannel channel;
    private GuestServiceGrpc.GuestServiceBlockingStub stub;

    @BeforeEach
    void startInProcessServer() throws IOException {
        applicationService = mock(GuestApplicationService.class);
        String serverName = InProcessServerBuilder.generateName();

        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new GuestGrpcService(applicationService))
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .build();
        stub = GuestServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void shutdown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    @DisplayName("GetGuest: 정상 경로 — Application Service 결과를 proto 로 변환")
    void getGuest_정상() {
        when(applicationService.findById(GUEST_ID)).thenReturn(
            new GuestResult(GUEST_ID, "길동", "홍", "hong@example.com", "+821012345678"));

        Guest response = stub.getGuest(GetGuestRequest.newBuilder().setId(GUEST_ID).build());

        assertThat(response.getId()).isEqualTo(GUEST_ID);
        assertThat(response.getFirstName()).isEqualTo("길동");
        assertThat(response.getLastName()).isEqualTo("홍");
        assertThat(response.getEmail()).isEqualTo("hong@example.com");
        assertThat(response.getPhoneNumber()).isEqualTo("+821012345678");
    }

    @Test
    @DisplayName("GetGuest: 존재하지 않는 id → NOT_FOUND")
    void getGuest_없으면_NOT_FOUND() {
        when(applicationService.findById(GUEST_ID))
            .thenThrow(new GuestNotFoundException(GuestId.of(GUEST_ID)));

        assertThatThrownBy(() -> stub.getGuest(GetGuestRequest.newBuilder().setId(GUEST_ID).build()))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    @DisplayName("GetGuest: 잘못된 UUID → INVALID_ARGUMENT")
    void getGuest_잘못된_UUID_INVALID_ARGUMENT() {
        when(applicationService.findById("not-a-uuid"))
            .thenThrow(new IllegalArgumentException("Invalid UUID string"));

        assertThatThrownBy(() -> stub.getGuest(GetGuestRequest.newBuilder().setId("not-a-uuid").build()))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    @DisplayName("GetGuest: 기타 런타임 예외 → INTERNAL")
    void getGuest_내부예외_INTERNAL() {
        when(applicationService.findById(GUEST_ID))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> stub.getGuest(GetGuestRequest.newBuilder().setId(GUEST_ID).build()))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL));
    }

    @Test
    @DisplayName("BatchGetGuests: 존재하는 투숙객만 반환 + 클라이언트 중복 id 는 dedupe 후 조회")
    void batchGetGuests_정상() {
        String id2 = UUID.fromString("01970000-0000-7000-8000-000000000002").toString();
        String missing = UUID.fromString("01970000-0000-7000-8000-0000000000ff").toString();
        Set<String> expectedIds = new LinkedHashSet<>(List.of(GUEST_ID, id2, missing));
        when(applicationService.findAllByIds(expectedIds)).thenReturn(List.of(
            new GuestResult(GUEST_ID, "길동", "홍", "hong@example.com", "+821012345678"),
            new GuestResult(id2, "Alice", "Kim", "alice@example.com", "+821099990000")
        ));

        BatchGetGuestsResponse response = stub.batchGetGuests(BatchGetGuestsRequest.newBuilder()
            // 동일 id 를 2번 넣어도 Application 에는 1번만 전달돼야 함
            .addIds(GUEST_ID).addIds(GUEST_ID).addIds(id2).addIds(missing)
            .build());

        assertThat(response.getGuestsCount()).isEqualTo(2);
        assertThat(response.getGuests(0).getId()).isEqualTo(GUEST_ID);
        assertThat(response.getGuests(1).getId()).isEqualTo(id2);
        verify(applicationService).findAllByIds(expectedIds);
    }

    @Test
    @DisplayName("BatchGetGuests: 크기 초과 → INVALID_ARGUMENT, Application Service 호출 없음")
    void batchGetGuests_크기_초과() {
        BatchGetGuestsRequest.Builder builder = BatchGetGuestsRequest.newBuilder();
        for (int i = 0; i <= GuestGrpcService.MAX_BATCH_SIZE; i++) {
            builder.addIds(UUID.randomUUID().toString());
        }
        BatchGetGuestsRequest request = builder.build();

        assertThatThrownBy(() -> stub.batchGetGuests(request))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    @DisplayName("BatchGetGuests: 빈 입력 — 빈 응답")
    void batchGetGuests_빈_입력() {
        when(applicationService.findAllByIds(new LinkedHashSet<>())).thenReturn(List.of());

        BatchGetGuestsResponse response = stub.batchGetGuests(BatchGetGuestsRequest.getDefaultInstance());

        assertThat(response.getGuestsCount()).isZero();
    }

    @Test
    @DisplayName("BatchGetGuests: 경계 크기 (MAX_BATCH_SIZE + 1) 에서 정확히 INVALID_ARGUMENT")
    void batchGetGuests_경계_크기() {
        BatchGetGuestsRequest.Builder builder = BatchGetGuestsRequest.newBuilder();
        // 명시 리터럴 101 — 상수 변경 회귀 검증 (test-reviewer M3)
        for (int i = 0; i < 101; i++) {
            builder.addIds(UUID.randomUUID().toString());
        }

        assertThatThrownBy(() -> stub.batchGetGuests(builder.build()))
            .isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }
}

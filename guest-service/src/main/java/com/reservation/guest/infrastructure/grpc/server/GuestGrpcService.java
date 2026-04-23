package com.reservation.guest.infrastructure.grpc.server;

import com.reservation.contracts.guest.BatchGetGuestsRequest;
import com.reservation.contracts.guest.BatchGetGuestsResponse;
import com.reservation.contracts.guest.GetGuestRequest;
import com.reservation.contracts.guest.Guest;
import com.reservation.contracts.guest.GuestServiceGrpc;
import com.reservation.guest.application.dto.GuestResult;
import com.reservation.guest.application.service.GuestApplicationService;
import com.reservation.guest.domain.exception.GuestNotFoundException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * guest-service 가 외부 서비스(reservation-service 등) 에게 제공하는 gRPC 서버.
 * contracts/guest.proto 의 {@code GuestService} 를 구현하며, Application Service 에
 * 위임해 Domain 로직 재사용을 보장한다 (CLAUDE.md 원칙 — Controller/gRPC 는 Application 경유).
 *
 * <p>예외 → gRPC {@link Status} 매핑 정책:
 * <ul>
 *   <li>{@link GuestNotFoundException} → {@code NOT_FOUND}</li>
 *   <li>{@link IllegalArgumentException} (잘못된 UUID · 입력 초과 등) → {@code INVALID_ARGUMENT}</li>
 *   <li>기타 {@link RuntimeException} → {@code INTERNAL} (원인은 로그로만)</li>
 * </ul>
 *
 * <p>{@link #batchGetGuests} 는 최대 {@link #MAX_BATCH_SIZE} 건으로 제한한다 — 인증 없는
 * 내부 gRPC 라고 해도 대량 조회 요청 하나가 서비스 전체 DB / 메모리를 점유하지 않도록 방어.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class GuestGrpcService extends GuestServiceGrpc.GuestServiceImplBase {

    static final int MAX_BATCH_SIZE = 100;

    private final GuestApplicationService applicationService;

    @Override
    public void getGuest(GetGuestRequest request, StreamObserver<Guest> responseObserver) {
        try {
            GuestResult result = applicationService.findById(request.getId());
            responseObserver.onNext(toProto(result));
            responseObserver.onCompleted();
        } catch (GuestNotFoundException e) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (RuntimeException e) {
            log.error("Unhandled error on GetGuest id={}", request.getId(), e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("internal error")
                .asRuntimeException());
        }
    }

    @Override
    public void batchGetGuests(BatchGetGuestsRequest request,
                               StreamObserver<BatchGetGuestsResponse> responseObserver) {
        try {
            List<String> ids = request.getIdsList();
            if (ids.size() > MAX_BATCH_SIZE) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("too many ids: max=" + MAX_BATCH_SIZE + ", was=" + ids.size())
                    .asRuntimeException());
                return;
            }

            // 클라이언트 실수로 중복 id 가 섞여 들어와도 DB 조회 부담을 줄이기 위해 dedupe.
            // LinkedHashSet 으로 요청 순서는 보존 (partial response 의 응답 순서 안정성).
            Set<String> uniqueIds = new LinkedHashSet<>(ids);
            List<GuestResult> results = applicationService.findAllByIds(uniqueIds);
            BatchGetGuestsResponse.Builder builder = BatchGetGuestsResponse.newBuilder();
            for (GuestResult r : results) {
                builder.addGuests(toProto(r));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (RuntimeException e) {
            log.error("Unhandled error on BatchGetGuests size={}",
                request.getIdsCount(), e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("internal error")
                .asRuntimeException());
        }
    }

    private static Guest toProto(GuestResult result) {
        return Guest.newBuilder()
            .setId(result.id())
            .setFirstName(result.firstName())
            .setLastName(result.lastName())
            .setEmail(result.email())
            .setPhoneNumber(result.phoneNumber())
            .build();
    }
}

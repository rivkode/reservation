package com.reservation.reservation.infrastructure.grpc.client;

import com.reservation.contracts.guest.GetGuestRequest;
import com.reservation.contracts.guest.GuestServiceGrpc;
import com.reservation.reservation.domain.model.GuestId;
import com.reservation.reservation.domain.service.GuestVerificationPort;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@link GuestVerificationPort} 의 gRPC 어댑터.
 *
 * <p>guest-service 의 {@code GuestService.GetGuest} 를 Deadline 3 초로 호출해 존재만
 * 확인한다 (반환 데이터는 본 PR 에서 사용하지 않음). 응답이 정상이면 정상 반환,
 * {@link Status.Code#NOT_FOUND} 는 {@link GuestVerificationPort.GuestNotFoundException},
 * 그 외 통신 실패 (UNAVAILABLE · DEADLINE_EXCEEDED · INTERNAL …) 는
 * {@link GuestVerificationPort.GuestVerificationUnavailableException} 으로 변환한다.
 *
 * <p>Resilience4j Circuit Breaker 적용은 PR-4.1 일괄 정비에서 도입 — 본 PR 은 Deadline
 * 만 적용한다 (사용자 승인).
 */
@Component
@Slf4j
public class GuestServiceGrpcClient implements GuestVerificationPort {

    static final long DEADLINE_MS = 3_000L;

    private final GuestServiceGrpc.GuestServiceBlockingStub stub;

    public GuestServiceGrpcClient(@GrpcClient("guest-service") GuestServiceGrpc.GuestServiceBlockingStub stub) {
        this.stub = stub;
    }

    @Override
    public void verify(GuestId guestId) {
        Objects.requireNonNull(guestId, "guestId");
        try {
            stub.withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                .getGuest(GetGuestRequest.newBuilder().setId(guestId.asString()).build());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                throw new GuestVerificationPort.GuestNotFoundException(guestId);
            }
            log.warn("guest-service GetGuest failed: code={} guestId={}", code, guestId.asString());
            throw new GuestVerificationPort.GuestVerificationUnavailableException(guestId, e);
        }
    }
}

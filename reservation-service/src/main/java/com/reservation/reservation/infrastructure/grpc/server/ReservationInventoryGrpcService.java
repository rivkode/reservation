package com.reservation.reservation.infrastructure.grpc.server;

import com.reservation.contracts.reservation.ReservationInventoryServiceGrpc;
import com.reservation.contracts.reservation.RoomTypeInventorySnapshot;
import com.reservation.contracts.reservation.StreamInventoryRequest;
import com.reservation.reservation.application.dto.InventorySnapshotResult;
import com.reservation.reservation.application.service.InventoryQueryApplicationService;
import com.reservation.reservation.domain.model.HotelId;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * reservation-service 가 외부(hotel-service 캐시 재구축 배치 · FR-H-08) 에게 제공하는
 * gRPC 서버. {@code contracts/reservation.proto} 의
 * {@link ReservationInventoryServiceGrpc} 를 구현하며, {@link InventoryQueryApplicationService}
 * 에 위임해 Domain / Repository 직접 접근을 차단한다 (CLAUDE.md — Controller/gRPC 는
 * Application 경유).
 *
 * <p>예외 → gRPC {@link Status} 매핑 정책 (guest-service {@code GuestGrpcService} 와 동일):
 * <ul>
 *   <li>{@link IllegalArgumentException} — 잘못된 UUID, {@code from > to}, 범위 상한 초과
 *       → {@code INVALID_ARGUMENT}</li>
 *   <li>{@link DateTimeParseException} — ISO-8601 형식 아님 → {@code INVALID_ARGUMENT}</li>
 *   <li>기타 {@link RuntimeException} → {@code INTERNAL} (원인은 로그로만)</li>
 * </ul>
 *
 * <p>존재하지 않는 호텔·빈 구간은 에러 아닌 빈 스트림 + {@code onCompleted()} 로 반환해
 * "객실 미등록 호텔" 같은 정상 시나리오를 흡수한다.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ReservationInventoryGrpcService
    extends ReservationInventoryServiceGrpc.ReservationInventoryServiceImplBase {

    private final InventoryQueryApplicationService applicationService;

    @Override
    public void streamInventory(StreamInventoryRequest request,
                                StreamObserver<RoomTypeInventorySnapshot> responseObserver) {
        HotelId hotelId;
        LocalDate from;
        LocalDate to;
        try {
            hotelId = HotelId.of(request.getHotelId());
            from = LocalDate.parse(request.getFromDate());
            to = LocalDate.parse(request.getToDate());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException());
            return;
        }

        try {
            List<InventorySnapshotResult> snapshots =
                applicationService.findByHotelInRange(hotelId, from, to);
            for (InventorySnapshotResult s : snapshots) {
                responseObserver.onNext(toProto(s));
            }
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException());
        } catch (RuntimeException e) {
            log.error("Unhandled error on StreamInventory hotelId={} from={} to={}",
                request.getHotelId(), request.getFromDate(), request.getToDate(), e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("internal error")
                .asRuntimeException());
        }
    }

    private static RoomTypeInventorySnapshot toProto(InventorySnapshotResult s) {
        return RoomTypeInventorySnapshot.newBuilder()
            .setHotelId(s.hotelId())
            .setRoomTypeId(s.roomTypeId())
            .setDate(s.stayDate().toString())
            .setTotalInventory(s.totalInventory())
            .setAvailable(s.available())
            .build();
    }
}

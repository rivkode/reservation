package com.reservation.reservation.infrastructure.grpc.client;

import com.reservation.contracts.rate.GetRoomTypeRateRequest;
import com.reservation.contracts.rate.RateServiceGrpc;
import com.reservation.contracts.rate.RoomTypeRate;
import com.reservation.reservation.domain.model.HotelId;
import com.reservation.reservation.domain.model.Money;
import com.reservation.reservation.domain.model.RoomTypeId;
import com.reservation.reservation.domain.service.RoomTypeRateQuotePort;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@link RoomTypeRateQuotePort} 의 gRPC 어댑터.
 *
 * <p>rate-service 의 {@code RateService.GetRoomTypeRate} 를 Deadline 3 초로 호출해
 * 1 박치 요금을 조회한다. 호출자(Application Service) 가 N 일치를 N 회 호출하고
 * {@link Money#add} 로 합산한다.
 *
 * <p>{@link Status.Code#NOT_FOUND} 는
 * {@link RoomTypeRateQuotePort.RoomTypeRateNotFoundException}, 그 외 통신 실패는
 * {@link RoomTypeRateQuotePort.RateServiceUnavailableException} 으로 변환.
 */
@Component
@Slf4j
public class RateServiceGrpcClient implements RoomTypeRateQuotePort {

    static final long DEADLINE_MS = 3_000L;

    private final RateServiceGrpc.RateServiceBlockingStub stub;

    public RateServiceGrpcClient(@GrpcClient("rate-service") RateServiceGrpc.RateServiceBlockingStub stub) {
        this.stub = stub;
    }

    @Override
    public Money quoteFor(HotelId hotelId, RoomTypeId roomTypeId, LocalDate stayDate) {
        Objects.requireNonNull(hotelId, "hotelId");
        Objects.requireNonNull(roomTypeId, "roomTypeId");
        Objects.requireNonNull(stayDate, "stayDate");

        GetRoomTypeRateRequest request = GetRoomTypeRateRequest.newBuilder()
            .setHotelId(hotelId.asString())
            .setRoomTypeId(roomTypeId.asString())
            .setDate(stayDate.toString())
            .build();
        try {
            RoomTypeRate response = stub
                .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                .getRoomTypeRate(request);
            return Money.of(response.getAmount(), response.getCurrency());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                throw new RoomTypeRateQuotePort.RoomTypeRateNotFoundException(
                    hotelId, roomTypeId, stayDate);
            }
            log.warn("rate-service GetRoomTypeRate failed: code={} hotel={} roomType={} date={}",
                code, hotelId.asString(), roomTypeId.asString(), stayDate);
            throw new RoomTypeRateQuotePort.RateServiceUnavailableException(
                hotelId, roomTypeId, stayDate, e);
        }
    }
}

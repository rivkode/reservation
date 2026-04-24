package com.reservation.hotel.application.dto;

/**
 * 캐시 재구축 배치 결과 요약. 관측성 용도 — 실패 호텔 수가 많으면 알림 트리거에 사용할 수
 * 있도록 개별 호텔 단위 집계를 노출한다.
 */
public record RebuildResult(int hotelsProcessed,
                            int hotelsFailed,
                            int entriesWritten) {

    public static RebuildResult empty() {
        return new RebuildResult(0, 0, 0);
    }
}

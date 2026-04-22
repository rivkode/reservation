package com.reservation.hotel.domain.model;

/**
 * Room soft-delete · 운영 상태 enum. 물리 삭제가 아닌 상태 전이로 이력을 보존한다.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — 예약 가능한 정상 상태.</li>
 *   <li>{@link #UNDER_MAINTENANCE} — 점검 등으로 한시적 비활성화. 예약 신규 생성 차단.</li>
 *   <li>{@link #DEACTIVATED} — 영구 폐지 (soft-delete). 이전 예약/이력은 보존하되 신규 예약 불가.</li>
 * </ul>
 *
 * <p>상태 전이 규칙은 {@code Room} Aggregate 가 enforce 한다 (DEACTIVATED 에서 복원 금지 등).
 */
public enum RoomStatus {
    ACTIVE,
    UNDER_MAINTENANCE,
    DEACTIVATED;

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isDeactivated() {
        return this == DEACTIVATED;
    }
}

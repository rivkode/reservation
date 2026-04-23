package com.reservation.reservation.domain.service;

import com.reservation.reservation.domain.model.GuestId;

/**
 * 예약 생성 시 투숙객의 존재를 외부 SoT(guest-service) 에 확인하는 도메인 포트.
 * Infrastructure 의 gRPC 클라이언트 어댑터가 본 인터페이스를 구현한다.
 *
 * <p>Domain 계층에 두는 이유:
 * <ul>
 *   <li>Application Service 가 gRPC stub 을 직접 알지 못하게 한다 (의존성 역전)</li>
 *   <li>인터페이스의 의미("guest 검증") 는 외부 transport 와 무관하므로 도메인 어휘로
 *       표현하는 것이 옳다 (module-boundary §5.2 가이드 일치)</li>
 * </ul>
 *
 * <p>구현은 다음 의미를 보장해야 한다:
 * <ul>
 *   <li>guest 가 존재하면 정상 반환 (반환값 없음 — 본 PR 은 존재 여부만 사용)</li>
 *   <li>guest 가 SoT 에 존재하지 않으면 {@link GuestNotFoundException}</li>
 *   <li>SoT 통신 자체가 실패(타임아웃 · 네트워크 · 서버 5xx) 하면
 *       {@link GuestVerificationUnavailableException}</li>
 * </ul>
 */
public interface GuestVerificationPort {

    void verify(GuestId guestId);

    /** 외부 SoT 에 guest 가 존재하지 않음 — 클라이언트 입력 오류 (404 매핑). */
    class GuestNotFoundException extends RuntimeException {
        public GuestNotFoundException(GuestId id) {
            super("Guest not found in SoT: " + id.asString());
        }
    }

    /** guest-service 통신 실패 — 일시적 외부 장애 (503 매핑). */
    class GuestVerificationUnavailableException extends RuntimeException {
        public GuestVerificationUnavailableException(GuestId id, Throwable cause) {
            super("Guest verification unavailable for: " + id.asString(), cause);
        }
    }
}

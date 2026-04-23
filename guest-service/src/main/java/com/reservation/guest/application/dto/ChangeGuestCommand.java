package com.reservation.guest.application.dto;

/**
 * 투숙객 변경 Command. PATCH 의 부분 변경을 지원하도록 각 필드는 nullable 이다 —
 * {@code null} 은 "변경 없음" 을 의미하고, 제공된 필드만 도메인 변경 메서드를 호출한다.
 *
 * <p>이름만 단독 변경 시 {@code firstName/lastName} 쌍 중 한쪽만 제공되면 어떻게
 * 할지 — 현재 정책: <b>둘 다 제공되어야 한다</b>. GuestName VO 가 묶음 단위로 설계되어
 * 있고, 부분 제공을 받으면 원래 값과 병합해 또 다른 불변식(문화권별 last-only 기호 등) 을
 * 내포시키게 된다. PATCH 호출자가 현재 값을 읽어 보낸 뒤 변경 필드만 교체하는 방식을
 * 권장.
 */
public record ChangeGuestCommand(
    String guestId,
    String firstName,
    String lastName,
    String email,
    String phoneNumber
) {
}

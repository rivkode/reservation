package com.reservation.guest.domain.model;

import java.util.Objects;

/**
 * 투숙객 이름 VO — firstName / lastName 묶음.
 *
 * <p>proto `Guest.first_name` · `Guest.last_name` 스키마와 1:1 매핑된다.
 * 항상 함께 다니는 필드를 VO 로 묶어 개별 전달 시 생길 수 있는 순서 혼동 · 부분 결측을
 * 컴파일 타임에 차단한다.
 *
 * <p>불변식:
 * <ul>
 *   <li>firstName · lastName 모두 non-blank (trim 후 빈 문자열 불가)</li>
 *   <li>각 필드 길이 ≤ {@link #MAX_LENGTH} — DB VARCHAR 상한을 도메인에서 재확인</li>
 *   <li>생성 시 양쪽 모두 trim 되어 내부에 보관된다.</li>
 * </ul>
 */
public record GuestName(String firstName, String lastName) {

    public static final int MAX_LENGTH = 100;

    public GuestName {
        firstName = normalize(firstName, "firstName");
        lastName = normalize(lastName, "lastName");
    }

    private static String normalize(String raw, String fieldName) {
        Objects.requireNonNull(raw, fieldName);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                fieldName + " must be at most " + MAX_LENGTH + " characters, was " + trimmed.length());
        }
        return trimmed;
    }
}

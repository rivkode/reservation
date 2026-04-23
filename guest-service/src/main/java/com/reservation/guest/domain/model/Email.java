package com.reservation.guest.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 이메일 VO — 저장·비교를 위해 lowercase 로 정규화된다.
 *
 * <p>ddd-architect 권고 H2 정합: "이메일 유일성" 불변식은 Aggregate 집합 경계로서
 * Application Service · DB UNIQUE 가 최종 방어하지만, VO 자체는 대소문자 구분 없는
 * 비교가 가능하도록 canonical 형식만 보관한다.
 *
 * <p>정규식은 실무 관례인 "대충 하나의 @ · 앞뒤 non-space · 뒤에 최소 1개의 .doma" 수준.
 * RFC 5321/5322 전수 검증은 과도하므로 거르지 않는다 — 불가능한 형식만 즉시 차단.
 */
public record Email(String value) {

    public static final int MAX_LENGTH = 254; // RFC 5321 §4.5.3.1.3

    private static final Pattern PATTERN = Pattern.compile(
        "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    public Email {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "Email must be at most " + MAX_LENGTH + " characters, was " + trimmed.length());
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(lower).matches()) {
            throw new IllegalArgumentException("Email format is invalid: " + trimmed);
        }
        value = lower;
    }
}

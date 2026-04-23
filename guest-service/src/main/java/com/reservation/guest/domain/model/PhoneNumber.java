package com.reservation.guest.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 전화번호 VO — 입력은 관대하게 받되 저장·비교는 E.164 canonical 형태로 정규화된다.
 *
 * <p>ddd-architect 권고 H1 정합. 하이픈 · 공백 · 괄호는 제거하고 선두 {@code +} 와
 * 숫자만 남긴다. 국가코드 없는 입력은 SMS · 국제 통화 맥락에서 모호하므로 Strict reject.
 *
 * <p>예:
 * <ul>
 *   <li>{@code "+82-10-1234-5678"} → {@code "+821012345678"}</li>
 *   <li>{@code "+82 (10) 1234 5678"} → {@code "+821012345678"}</li>
 *   <li>{@code "010-1234-5678"} → 400 (국가코드 누락)</li>
 * </ul>
 *
 * <p>E.164 전체 길이 상한 15자리 숫자 + 선두 {@code +} = 총 16자 상한.
 */
public record PhoneNumber(String value) {

    private static final Pattern STRIP_PATTERN = Pattern.compile("[\\s()\\-]");
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9][0-9]{4,14}$");

    public PhoneNumber {
        Objects.requireNonNull(value, "value");
        String stripped = STRIP_PATTERN.matcher(value.trim()).replaceAll("");
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("PhoneNumber must not be blank");
        }
        if (!E164_PATTERN.matcher(stripped).matches()) {
            throw new IllegalArgumentException(
                "PhoneNumber must be E.164 format (\"+\" followed by 5~15 digits, leading digit non-zero): "
                    + value);
        }
        value = stripped;
    }
}

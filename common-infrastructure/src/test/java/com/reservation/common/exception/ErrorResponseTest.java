package com.reservation.common.exception;

import com.reservation.common.exception.ErrorResponse.FieldViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorResponseTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-04-21T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    @DisplayName("of() 팩토리는 주입된 Clock 으로 timestamp 를 확정해 ErrorResponse 를 구성한다")
    void ofFactoryStampsTimestampFromClock() {
        ErrorResponse response = ErrorResponse.of(
            400,
            CommonErrorCode.VALIDATION_FAILED,
            "checkInDate is required",
            "/api/v1/reservations",
            List.of(new FieldViolation("checkInDate", "must not be null")),
            FIXED_CLOCK
        );

        assertThat(response.timestamp()).isEqualTo(FIXED_INSTANT);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.message()).isEqualTo("checkInDate is required");
        assertThat(response.path()).isEqualTo("/api/v1/reservations");
        assertThat(response.fieldViolations())
            .singleElement()
            .satisfies(v -> {
                assertThat(v.field()).isEqualTo("checkInDate");
                assertThat(v.reason()).isEqualTo("must not be null");
            });
    }

    @Test
    @DisplayName("fieldViolations 가 null 이면 빈 리스트로 정규화된다")
    void nullFieldViolationsBecomesEmpty() {
        ErrorResponse response = new ErrorResponse(
            FIXED_INSTANT, 500, "INTERNAL_ERROR", "unexpected", "/x", null
        );

        assertThat(response.fieldViolations()).isEmpty();
    }

    @Test
    @DisplayName("of() 팩토리에 fieldViolations=null 을 넘겨도 빈 리스트로 정규화된다")
    void ofFactoryNormalisesNullFieldViolations() {
        ErrorResponse response = ErrorResponse.of(
            500, CommonErrorCode.INTERNAL_ERROR, "boom", "/x", null, FIXED_CLOCK
        );

        assertThat(response.fieldViolations()).isEmpty();
    }

    @Test
    @DisplayName("path 는 선택 필드이므로 null 을 허용한다")
    void allowsNullPath() {
        ErrorResponse response = new ErrorResponse(
            FIXED_INSTANT, 400, "VALIDATION_FAILED", "bad", null, List.of()
        );

        assertThat(response.path()).isNull();
    }

    @Test
    @DisplayName("fieldViolations 는 외부에서 수정할 수 없는 불변 리스트로 보관된다")
    void fieldViolationsIsImmutable() {
        List<FieldViolation> mutable = new ArrayList<>();
        mutable.add(new FieldViolation("a", "r"));

        ErrorResponse response = new ErrorResponse(
            FIXED_INSTANT, 400, "VALIDATION_FAILED", "bad", "/x", mutable
        );
        mutable.add(new FieldViolation("b", "r2"));

        assertThat(response.fieldViolations()).hasSize(1);
        assertThatThrownBy(() -> response.fieldViolations().add(new FieldViolation("c", "r3")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("timestamp · code · message 중 하나라도 null 이면 NPE 로 빠르게 실패한다")
    void rejectsNullRequiredFields() {
        assertThatThrownBy(() -> new ErrorResponse(null, 400, "X", "msg", "/x", List.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("timestamp");

        assertThatThrownBy(() -> new ErrorResponse(FIXED_INSTANT, 400, null, "msg", "/x", List.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("code");

        assertThatThrownBy(() -> new ErrorResponse(FIXED_INSTANT, 400, "X", null, "/x", List.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("message");
    }

    @Test
    @DisplayName("FieldViolation 도 필수 필드 null 을 거부한다")
    void fieldViolationRejectsNulls() {
        assertThatThrownBy(() -> new FieldViolation(null, "r"))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("field");
        assertThatThrownBy(() -> new FieldViolation("f", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("reason");
    }
}

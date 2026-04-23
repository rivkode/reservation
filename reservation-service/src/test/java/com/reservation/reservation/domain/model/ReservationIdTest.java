package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("ReservationId / GuestId VO")
class ReservationIdTest {

    private static final String VALID_UUID = "01933333-1111-7aaa-9aaa-111122223333";

    @Test
    @DisplayName("ReservationId.newId() 는 UUID v7 (version nibble = 7) 을 발급한다")
    void newIdGeneratesUuidV7() {
        ReservationId id = ReservationId.newId();

        assertThat(id.value().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("ReservationId.newId() 두 번 호출은 단조 증가한다 (UUID v7 시간 기반)")
    void newIdMonotonic() {
        ReservationId first = ReservationId.newId();
        ReservationId second = ReservationId.newId();

        assertThat(first.asString()).isLessThan(second.asString());
    }

    @Test
    @DisplayName("ReservationId / GuestId 모두 String 파싱 · UUID 직접 주입을 지원한다")
    void parseAndOf() {
        assertThat(ReservationId.of(VALID_UUID).asString()).isEqualTo(VALID_UUID);
        assertThat(GuestId.of(VALID_UUID).asString()).isEqualTo(VALID_UUID);

        UUID uuid = UUID.fromString(VALID_UUID);
        assertThat(ReservationId.of(uuid).value()).isEqualTo(uuid);
        assertThat(GuestId.of(uuid).value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("null UUID 는 NPE")
    void rejectsNullValue() {
        assertThatNullPointerException().isThrownBy(() -> new ReservationId(null));
        assertThatNullPointerException().isThrownBy(() -> new GuestId(null));
    }

    @Test
    @DisplayName("잘못된 UUID 문자열은 IllegalArgument")
    void rejectsMalformedString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> ReservationId.of("not-a-uuid"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> GuestId.of("not-a-uuid"));
    }

    @Test
    @DisplayName("두 ID 의 equality 는 UUID 값 기준")
    void equalityByValue() {
        ReservationId a = ReservationId.of(VALID_UUID);
        ReservationId b = ReservationId.of(VALID_UUID);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}

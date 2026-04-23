package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("식별자 VO (HotelId/RoomTypeId/RoomId)")
class IdValueObjectsTest {

    private static final String VALID_UUID = "01933333-1111-7aaa-9aaa-111122223333";

    @Test
    @DisplayName("UUID String → VO 파싱")
    void parseFromString() {
        assertThat(HotelId.of(VALID_UUID).asString()).isEqualTo(VALID_UUID);
        assertThat(RoomTypeId.of(VALID_UUID).asString()).isEqualTo(VALID_UUID);
        assertThat(RoomId.of(VALID_UUID).asString()).isEqualTo(VALID_UUID);
    }

    @Test
    @DisplayName("UUID 직접 주입")
    void ofUuid() {
        UUID uuid = UUID.fromString(VALID_UUID);
        assertThat(HotelId.of(uuid).value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("null UUID 는 NPE")
    void rejectsNullValue() {
        assertThatNullPointerException().isThrownBy(() -> new HotelId(null));
        assertThatNullPointerException().isThrownBy(() -> new RoomTypeId(null));
        assertThatNullPointerException().isThrownBy(() -> new RoomId(null));
    }

    @Test
    @DisplayName("null String 은 NPE")
    void rejectsNullString() {
        assertThatNullPointerException().isThrownBy(() -> HotelId.of((String) null));
    }

    @Test
    @DisplayName("잘못된 UUID 문자열은 IllegalArgument — poison event 조기 차단")
    void rejectsMalformedString() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> HotelId.of("not-a-uuid"));
    }

    @Test
    @DisplayName("두 ID 인스턴스의 equality 는 UUID 값 기준")
    void equalityByValue() {
        HotelId a = HotelId.of(VALID_UUID);
        HotelId b = HotelId.of(VALID_UUID);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}

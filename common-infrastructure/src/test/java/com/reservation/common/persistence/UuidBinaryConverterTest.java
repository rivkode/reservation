package com.reservation.common.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UuidBinaryConverterTest {

    private final UuidBinaryConverter converter = new UuidBinaryConverter();

    @Test
    @DisplayName("UUID 를 16 byte 로 변환하고 round-trip 복원한다")
    void roundTripsUuidViaByteArray() {
        UUID original = UUID.fromString("018f4a9b-2c4d-7c34-8e9a-000000000000");

        byte[] encoded = converter.convertToDatabaseColumn(original);
        UUID decoded = converter.convertToEntityAttribute(encoded);

        assertThat(encoded).hasSize(16);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    @DisplayName("null 입력은 null 로 통과시킨다")
    void nullInputsPassThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("16 byte 순서는 big-endian — MSB 먼저, LSB 뒤")
    void bigEndianLayout() {
        UUID uuid = new UUID(0x0102030405060708L, 0x090A0B0C0D0E0F10L);

        byte[] encoded = converter.convertToDatabaseColumn(uuid);

        assertThat(encoded[0]).isEqualTo((byte) 0x01);
        assertThat(encoded[7]).isEqualTo((byte) 0x08);
        assertThat(encoded[8]).isEqualTo((byte) 0x09);
        assertThat(encoded[15]).isEqualTo((byte) 0x10);
    }

    @Test
    @DisplayName("high-bit(0x80 이상) 바이트가 포함된 UUID 도 부호 확장 없이 round-trip 한다")
    void roundTripsHighBitBytesWithoutSignExtension() {
        UUID uuid = new UUID(0xFFEEDDCCBBAA9988L, 0x7766554433221100L);

        byte[] encoded = converter.convertToDatabaseColumn(uuid);

        assertThat(encoded[0]).isEqualTo((byte) 0xFF);
        assertThat(encoded[1]).isEqualTo((byte) 0xEE);
        assertThat(converter.convertToEntityAttribute(encoded)).isEqualTo(uuid);
    }

    @Test
    @DisplayName("16 byte 가 아닌 입력은 IllegalArgumentException 으로 빠르게 실패한다")
    void rejectsWrongLengthByteArray() {
        byte[] tooShort = new byte[15];

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tooShort))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("16 bytes");
    }
}

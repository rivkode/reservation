package com.reservation.common.persistence;

import com.reservation.common.domain.DomainId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UUID ↔ {@code BINARY(16)} JPA AttributeConverter. ADR 0001 의 단일 변환 지점.
 *
 * <p>MSB/LSB 순서는 big-endian (RFC 4122 표준). MySQL {@code BINARY(16)} 컬럼에
 * 저장되어 {@code VARCHAR(36)} 대비 50% 공간 절감 + 인덱스 탐색 성능 개선.
 *
 * <p>각 서비스의 {@code *JpaEntity} 에서
 * {@code @Convert(converter = UuidBinaryConverter.class)} 로 사용한다. 서비스
 * 도메인의 {@link DomainId} 구체 record 는 Mapper 층에서 {@code UUID} 로 unwrap
 * 해 저장하고, 조회 시 다시 wrap 한다.
 */
@Converter(autoApply = false)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(UUID attribute) {
        if (attribute == null) {
            return null;
        }
        return ByteBuffer.allocate(16)
            .putLong(attribute.getMostSignificantBits())
            .putLong(attribute.getLeastSignificantBits())
            .array();
    }

    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        if (dbData.length != 16) {
            // 스키마 오류 · 잘못된 컬럼 타입 매핑 시 BufferUnderflowException 이 raw 로
            // 터지는 대신 맥락 있는 메시지를 남긴다.
            throw new IllegalArgumentException(
                "UUID binary column must be 16 bytes, got " + dbData.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }
}

package com.reservation.common.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUG (java-uuid-generator) 가 실제 비트 조작 · monotonic · clock-backwards 방어를
 * 자체 테스트로 보장하므로, 본 테스트는 프로젝트가 올바른 generator 를 선택했고
 * 기본 계약이 유지되는지 smoke 수준으로 고정한다.
 */
class UuidV7Test {

    @Test
    @DisplayName("생성된 UUID 는 version=7, variant=RFC 4122 (2) 비트 세팅을 가진다")
    void generatedUuidHasVersion7AndRfc4122Variant() {
        UUID uuid = UuidV7.create();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("연속 생성된 UUID 는 lexicographic 순서가 생성 순서와 일치한다 (same-ms monotonic)")
    void consecutiveUuidsAreLexicographicallyOrdered() {
        UUID[] sequence = IntStream.range(0, 1_000)
            .mapToObj(i -> UuidV7.create())
            .toArray(UUID[]::new);

        for (int i = 1; i < sequence.length; i++) {
            assertThat(sequence[i - 1].toString())
                .as("index %d (%s) should come before %d (%s)", i - 1, sequence[i - 1], i, sequence[i])
                .isLessThan(sequence[i].toString());
        }
    }

    @Test
    @DisplayName("대량 생성 시 UUID 는 모두 고유하다 (collision 없음)")
    void generatedUuidsAreUnique() {
        Set<UUID> generated = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            generated.add(UuidV7.create());
        }

        assertThat(generated).hasSize(10_000);
    }
}

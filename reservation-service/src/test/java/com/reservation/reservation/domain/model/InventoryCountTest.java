package com.reservation.reservation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("InventoryCount VO")
class InventoryCountTest {

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("0 이상은 허용")
        void acceptsNonNegative() {
            assertThat(InventoryCount.of(0).value()).isZero();
            assertThat(InventoryCount.of(5).value()).isEqualTo(5);
        }

        @Test
        @DisplayName("음수는 거부")
        void rejectsNegative() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> InventoryCount.of(-1))
                .withMessageContaining("non-negative");
        }

        @Test
        @DisplayName("zero() 는 0")
        void zeroFactory() {
            assertThat(InventoryCount.zero().value()).isZero();
            assertThat(InventoryCount.zero().isZero()).isTrue();
        }
    }

    @Nested
    @DisplayName("increment/decrement")
    class Mutation {

        @Test
        @DisplayName("increment 는 새 인스턴스 +1")
        void increment() {
            InventoryCount original = InventoryCount.of(3);
            InventoryCount incremented = original.increment();

            assertThat(incremented.value()).isEqualTo(4);
            assertThat(original.value()).as("원본 불변 유지").isEqualTo(3);
        }

        @Test
        @DisplayName("decrement 는 새 인스턴스 -1")
        void decrement() {
            InventoryCount original = InventoryCount.of(3);
            InventoryCount decremented = original.decrement();

            assertThat(decremented.value()).isEqualTo(2);
            assertThat(original.value()).isEqualTo(3);
        }

        @Test
        @DisplayName("0 에서 decrement 는 IllegalState")
        void decrementBelowZero() {
            assertThatIllegalStateException()
                .isThrownBy(() -> InventoryCount.zero().decrement())
                .withMessageContaining("below zero");
        }
    }
}

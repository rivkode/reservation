package com.reservation.rate.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    private static final UUID SAMPLE_UUID = UUID.fromString("01970000-0000-7000-8000-000000000001");

    @Nested
    @DisplayName("RateId")
    class RateIdTest {

        @Test
        void newId_생성_시_UUID_v7_발급() {
            RateId id = RateId.newId();

            assertThat(id.value()).isNotNull();
            // UUID v7 의 version nibble 은 0x7
            assertThat(id.value().version()).isEqualTo(7);
        }

        @Test
        void of_String_은_UUID_로_파싱() {
            RateId id = RateId.of(SAMPLE_UUID.toString());

            assertThat(id.value()).isEqualTo(SAMPLE_UUID);
            assertThat(id.asString()).isEqualTo(SAMPLE_UUID.toString());
        }

        @Test
        void null_은_거부() {
            assertThatThrownBy(() -> new RateId(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("HotelId / RoomTypeId (외부 참조 VO)")
    class ForeignIdTest {

        @Test
        void HotelId_of_String_정상_파싱() {
            HotelId id = HotelId.of(SAMPLE_UUID.toString());

            assertThat(id.asString()).isEqualTo(SAMPLE_UUID.toString());
        }

        @Test
        void HotelId_null_거부() {
            assertThatThrownBy(() -> new HotelId(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void HotelId_잘못된_UUID_문자열_거부() {
            assertThatThrownBy(() -> HotelId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void RoomTypeId_of_String_정상_파싱() {
            RoomTypeId id = RoomTypeId.of(SAMPLE_UUID.toString());

            assertThat(id.asString()).isEqualTo(SAMPLE_UUID.toString());
        }

        @Test
        void RoomTypeId_null_거부() {
            assertThatThrownBy(() -> new RoomTypeId(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Money")
    class MoneyTest {

        @Test
        void 유효한_금액과_통화로_생성() {
            Money money = Money.of(150_000L, "KRW");

            assertThat(money.amount()).isEqualTo(150_000L);
            assertThat(money.currency()).isEqualTo(Currency.getInstance("KRW"));
            assertThat(money.currencyCode()).isEqualTo("KRW");
        }

        @Test
        void 금액_0_은_허용() {
            Money money = Money.of(0L, "USD");

            assertThat(money.amount()).isZero();
        }

        @Test
        void 음수_금액_거부() {
            assertThatThrownBy(() -> Money.of(-1L, "KRW"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
        }

        @Test
        void null_통화_거부() {
            assertThatThrownBy(() -> new Money(1_000L, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void 무효_통화_코드_거부() {
            assertThatThrownBy(() -> Money.of(1_000L, "ZZZ"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 동일_금액_통화_equals_성립() {
            Money a = Money.of(100L, "KRW");
            Money b = Money.of(100L, "KRW");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void 통화가_다르면_not_equal() {
            Money krw = Money.of(100L, "KRW");
            Money usd = Money.of(100L, "USD");

            assertThat(krw).isNotEqualTo(usd);
        }
    }
}

package com.reservation.guest.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {

    private static final UUID SAMPLE_UUID = UUID.fromString("01970000-0000-7000-8000-000000000001");

    @Nested
    @DisplayName("GuestId")
    class GuestIdTest {

        @Test
        void newId_생성_시_UUID_v7_발급() {
            GuestId id = GuestId.newId();

            assertThat(id.value()).isNotNull();
            assertThat(id.value().version()).isEqualTo(7);
        }

        @Test
        void of_String_은_UUID_로_파싱() {
            GuestId id = GuestId.of(SAMPLE_UUID.toString());

            assertThat(id.value()).isEqualTo(SAMPLE_UUID);
            assertThat(id.asString()).isEqualTo(SAMPLE_UUID.toString());
        }

        @Test
        void null_은_거부() {
            assertThatThrownBy(() -> new GuestId(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void 잘못된_UUID_문자열_거부() {
            assertThatThrownBy(() -> GuestId.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("GuestName")
    class GuestNameTest {

        @Test
        void 정상_생성() {
            GuestName name = new GuestName("길동", "홍");

            assertThat(name.firstName()).isEqualTo("길동");
            assertThat(name.lastName()).isEqualTo("홍");
        }

        @Test
        void 앞뒤_공백_정규화() {
            GuestName name = new GuestName("  Alice  ", "\tKim\n");

            assertThat(name.firstName()).isEqualTo("Alice");
            assertThat(name.lastName()).isEqualTo("Kim");
        }

        @Test
        void 공백만_있는_firstName_거부() {
            assertThatThrownBy(() -> new GuestName("   ", "Kim"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstName");
        }

        @Test
        void 공백만_있는_lastName_거부() {
            assertThatThrownBy(() -> new GuestName("Alice", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastName");
        }

        @Test
        void null_필드_거부() {
            assertThatThrownBy(() -> new GuestName(null, "Kim"))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new GuestName("Alice", null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void 최대_길이_초과_거부() {
            String tooLong = "a".repeat(GuestName.MAX_LENGTH + 1);

            assertThatThrownBy(() -> new GuestName(tooLong, "Kim"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstName");
        }

        @Test
        void 동일_값_equals_성립() {
            GuestName a = new GuestName("Alice", "Kim");
            GuestName b = new GuestName("Alice", "Kim");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }
    }

    @Nested
    @DisplayName("Email")
    class EmailTest {

        @Test
        void 정상_이메일_생성() {
            Email email = new Email("alice@example.com");

            assertThat(email.value()).isEqualTo("alice@example.com");
        }

        @Test
        void 대문자_입력은_lowercase_로_정규화() {
            Email email = new Email("  Alice.Kim@EXAMPLE.COM ");

            assertThat(email.value()).isEqualTo("alice.kim@example.com");
        }

        @Test
        void 형식_오류_거부() {
            assertThatThrownBy(() -> new Email("no-at-sign"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Email("two@at@signs"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Email("no-dot@localhost"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Email("whitespace in@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void null_또는_빈_값_거부() {
            assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Email(""))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Email("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 최대_길이_초과_거부() {
            // MAX_LENGTH = 254. local 250 + "@x.co"(5) = 255 → 거부.
            String local = "a".repeat(250);
            String overLong = local + "@x.co";

            assertThatThrownBy(() -> new Email(overLong))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 최대_길이_정확히_MAX_LENGTH_는_허용() {
            // off-by-one 회귀 방어: local 249 + "@x.co"(5) = 254 → 허용.
            String local = "a".repeat(249);
            String exact = local + "@x.co";
            assertThat(exact.length()).isEqualTo(Email.MAX_LENGTH);

            Email email = new Email(exact);

            assertThat(email.value().length()).isEqualTo(Email.MAX_LENGTH);
        }

        @Test
        void 대소문자_다른_이메일_equals_성립() {
            Email a = new Email("Alice@Example.com");
            Email b = new Email("alice@example.com");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }
    }

    @Nested
    @DisplayName("PhoneNumber")
    class PhoneNumberTest {

        @Test
        void E164_문자열_그대로_허용() {
            PhoneNumber phone = new PhoneNumber("+821012345678");

            assertThat(phone.value()).isEqualTo("+821012345678");
        }

        @Test
        void 하이픈_공백_괄호_제거() {
            PhoneNumber hyphen = new PhoneNumber("+82-10-1234-5678");
            PhoneNumber spaced = new PhoneNumber("+82 10 1234 5678");
            PhoneNumber mixed = new PhoneNumber(" +82 (10) 1234-5678 ");

            assertThat(hyphen.value()).isEqualTo("+821012345678");
            assertThat(spaced.value()).isEqualTo("+821012345678");
            assertThat(mixed.value()).isEqualTo("+821012345678");
        }

        @Test
        void 국가코드_없는_입력_거부() {
            assertThatThrownBy(() -> new PhoneNumber("010-1234-5678"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PhoneNumber("01012345678"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 선두_0_국가코드_거부() {
            assertThatThrownBy(() -> new PhoneNumber("+0123456"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 너무_짧은_번호_거부() {
            assertThatThrownBy(() -> new PhoneNumber("+1234"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 너무_긴_번호_거부() {
            assertThatThrownBy(() -> new PhoneNumber("+1234567890123456"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void null_또는_빈_값_거부() {
            assertThatThrownBy(() -> new PhoneNumber(null))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new PhoneNumber("   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 동일_정규화_결과_equals_성립() {
            PhoneNumber a = new PhoneNumber("+82-10-1234-5678");
            PhoneNumber b = new PhoneNumber("+82 10 1234 5678");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }
    }
}

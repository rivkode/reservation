package com.reservation.guest.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestTest {

    private static final GuestName NAME = new GuestName("길동", "홍");
    private static final Email EMAIL = new Email("hong@example.com");
    private static final PhoneNumber PHONE = new PhoneNumber("+82-10-1234-5678");
    private static final Instant T0 = Instant.parse("2026-04-23T10:00:00Z");
    private static final Clock FIXED = Clock.fixed(T0, ZoneOffset.UTC);

    @Test
    @DisplayName("create: 신규 GuestId 발급 + createdAt = updatedAt = Clock")
    void create_신규_등록() {
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        assertThat(guest.id()).isNotNull();
        assertThat(guest.id().value().version()).isEqualTo(7);
        assertThat(guest.name()).isEqualTo(NAME);
        assertThat(guest.email()).isEqualTo(EMAIL);
        assertThat(guest.phoneNumber()).isEqualTo(PHONE);
        assertThat(guest.version()).isZero();
        assertThat(guest.createdAt()).isEqualTo(T0);
        assertThat(guest.updatedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("create: null 인자 즉시 거부")
    void create_null_거부() {
        assertThatThrownBy(() -> Guest.create(null, EMAIL, PHONE, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Guest.create(NAME, null, PHONE, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Guest.create(NAME, EMAIL, null, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Guest.create(NAME, EMAIL, PHONE, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("changeName: 다른 값이면 true + name · updatedAt 갱신")
    void changeName_변경() {
        Instant t1 = T0.plusSeconds(60);
        Clock later = Clock.fixed(t1, ZoneOffset.UTC);
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        boolean changed = guest.changeName(new GuestName("Alice", "Kim"), later);

        assertThat(changed).isTrue();
        assertThat(guest.name()).isEqualTo(new GuestName("Alice", "Kim"));
        assertThat(guest.updatedAt()).isEqualTo(t1);
        assertThat(guest.createdAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("changeName: 동일 값이면 false + no-op")
    void changeName_동일_값_noop() {
        Instant t1 = T0.plusSeconds(60);
        Clock later = Clock.fixed(t1, ZoneOffset.UTC);
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        boolean changed = guest.changeName(new GuestName("길동", "홍"), later);

        assertThat(changed).isFalse();
        assertThat(guest.updatedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("changeEmail: 다른 값이면 true + email · updatedAt 갱신")
    void changeEmail_변경() {
        Instant t1 = T0.plusSeconds(60);
        Clock later = Clock.fixed(t1, ZoneOffset.UTC);
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        boolean changed = guest.changeEmail(new Email("new@example.com"), later);

        assertThat(changed).isTrue();
        assertThat(guest.email()).isEqualTo(new Email("new@example.com"));
        assertThat(guest.updatedAt()).isEqualTo(t1);
    }

    @Test
    @DisplayName("changeEmail: 대소문자만 다른 동일 이메일은 no-op")
    void changeEmail_대소문자만_달라도_noop() {
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        boolean changed = guest.changeEmail(new Email("HONG@EXAMPLE.COM"), FIXED);

        assertThat(changed).isFalse();
    }

    @Test
    @DisplayName("changePhoneNumber: 동일 정규화 결과는 no-op")
    void changePhoneNumber_동일_정규화_noop() {
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        boolean changed = guest.changePhoneNumber(new PhoneNumber("+82 10 1234 5678"), FIXED);

        assertThat(changed).isFalse();
    }

    @Test
    @DisplayName("changePhoneNumber: 다른 번호면 true + 갱신")
    void changePhoneNumber_변경() {
        Instant t1 = T0.plusSeconds(60);
        Clock later = Clock.fixed(t1, ZoneOffset.UTC);
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        boolean changed = guest.changePhoneNumber(new PhoneNumber("+82-10-9999-0000"), later);

        assertThat(changed).isTrue();
        assertThat(guest.phoneNumber().value()).isEqualTo("+821099990000");
        assertThat(guest.updatedAt()).isEqualTo(t1);
    }

    @Test
    @DisplayName("change 계열: null 인자 거부")
    void change_null_거부() {
        Guest guest = Guest.create(NAME, EMAIL, PHONE, FIXED);

        assertThatThrownBy(() -> guest.changeName(null, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> guest.changeName(NAME, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> guest.changeEmail(null, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> guest.changeEmail(EMAIL, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> guest.changePhoneNumber(null, FIXED))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> guest.changePhoneNumber(PHONE, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("restore: 영속 상태 그대로 주입")
    void restore_복원() {
        GuestId id = GuestId.newId();
        Guest guest = Guest.restore(id, NAME, EMAIL, PHONE, 3L, T0, T0.plusSeconds(1));

        assertThat(guest.id()).isEqualTo(id);
        assertThat(guest.version()).isEqualTo(3L);
        assertThat(guest.createdAt()).isEqualTo(T0);
        assertThat(guest.updatedAt()).isEqualTo(T0.plusSeconds(1));
    }
}

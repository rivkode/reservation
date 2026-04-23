package com.reservation.guest.application.service;

import com.reservation.guest.application.dto.ChangeGuestCommand;
import com.reservation.guest.application.dto.GuestResult;
import com.reservation.guest.application.dto.RegisterGuestCommand;
import com.reservation.guest.domain.exception.DuplicateEmailException;
import com.reservation.guest.domain.exception.GuestNotFoundException;
import com.reservation.guest.domain.model.Email;
import com.reservation.guest.domain.model.Guest;
import com.reservation.guest.domain.model.GuestId;
import com.reservation.guest.domain.model.GuestName;
import com.reservation.guest.domain.model.PhoneNumber;
import com.reservation.guest.domain.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestApplicationServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-04-23T10:00:00Z"), ZoneOffset.UTC);
    private static final GuestName NAME = new GuestName("길동", "홍");
    private static final Email EMAIL = new Email("hong@example.com");
    private static final PhoneNumber PHONE = new PhoneNumber("+82-10-1234-5678");

    private GuestRepository repository;
    private GuestApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(GuestRepository.class);
        service = new GuestApplicationService(repository, FIXED);
    }

    @Test
    @DisplayName("register: 저장 후 Result 반환 — 이벤트 발행 없음 (PRD §7.2)")
    void register_저장_후_Result_반환() {
        when(repository.existsByEmail(EMAIL)).thenReturn(false);
        when(repository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

        GuestResult result = service.register(
            new RegisterGuestCommand("길동", "홍", "hong@example.com", "+82-10-1234-5678"));

        assertThat(result.firstName()).isEqualTo("길동");
        assertThat(result.lastName()).isEqualTo("홍");
        assertThat(result.email()).isEqualTo("hong@example.com");
        assertThat(result.phoneNumber()).isEqualTo("+821012345678");

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(repository).save(captor.capture());
        Guest saved = captor.getValue();
        assertThat(saved.email()).isEqualTo(EMAIL);
        assertThat(saved.phoneNumber().value()).isEqualTo("+821012345678");
    }

    @Test
    @DisplayName("register: 이메일 선제 중복이면 DuplicateEmailException + save 없음")
    void register_이메일_중복_거부() {
        when(repository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register(
            new RegisterGuestCommand("길동", "홍", "hong@example.com", "+82-10-1234-5678")))
            .isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("register: save 단계 DataIntegrityViolation 은 DuplicateEmailException 으로 변환")
    void register_race_condition_DB_제약_위반_변환() {
        when(repository.existsByEmail(EMAIL)).thenReturn(false);
        when(repository.save(any(Guest.class)))
            .thenThrow(new DataIntegrityViolationException("uk_guest_email"));

        assertThatThrownBy(() -> service.register(
            new RegisterGuestCommand("길동", "홍", "hong@example.com", "+82-10-1234-5678")))
            .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("register: 잘못된 VO 입력은 existsByEmail 호출 전에 IllegalArgumentException")
    void register_VO_불변식_위반() {
        assertThatThrownBy(() -> service.register(
            new RegisterGuestCommand("", "홍", "hong@example.com", "+82-10-1234-5678")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register(
            new RegisterGuestCommand("길동", "홍", "not-an-email", "+82-10-1234-5678")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register(
            new RegisterGuestCommand("길동", "홍", "hong@example.com", "010-1234-5678")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).existsByEmail(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("change: name / email / phone 모두 변경 시 한 번의 save + Result 반환")
    void change_모든_필드_변경() {
        Guest existing = Guest.create(NAME, EMAIL, PHONE, FIXED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.existsByEmail(new Email("new@example.com"))).thenReturn(false);
        when(repository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.change(new ChangeGuestCommand(
            existing.id().asString(), "Alice", "Kim", "new@example.com", "+82-10-9999-0000"));

        assertThat(existing.name()).isEqualTo(new GuestName("Alice", "Kim"));
        assertThat(existing.email().value()).isEqualTo("new@example.com");
        assertThat(existing.phoneNumber().value()).isEqualTo("+821099990000");
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("change: 동일 값만 제공되면 no-op — save 호출 없음, Result 는 현재 상태")
    void change_전부_동일_값_noop() {
        Guest existing = Guest.create(NAME, EMAIL, PHONE, FIXED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));

        GuestResult result = service.change(new ChangeGuestCommand(
            existing.id().asString(), "길동", "홍", "hong@example.com", "+82-10-1234-5678"));

        assertThat(result.firstName()).isEqualTo("길동");
        verify(repository, never()).save(any());
        verify(repository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("change: 이메일 변경 시 중복이면 DuplicateEmailException + save 없음")
    void change_이메일_중복_거부() {
        Guest existing = Guest.create(NAME, EMAIL, PHONE, FIXED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.existsByEmail(new Email("taken@example.com"))).thenReturn(true);

        assertThatThrownBy(() -> service.change(new ChangeGuestCommand(
            existing.id().asString(), null, null, "taken@example.com", null)))
            .isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("change: 동일 이메일 재전송은 existsByEmail 검증 없이 no-op")
    void change_이메일_동일값_중복검증_스킵() {
        Guest existing = Guest.create(NAME, EMAIL, PHONE, FIXED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));

        service.change(new ChangeGuestCommand(
            existing.id().asString(), null, null, "hong@example.com", null));

        verify(repository, never()).existsByEmail(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("change: firstName 만 단독 제공 시 IllegalArgumentException (GuestName 묶음 정책)")
    void change_이름_한쪽만_제공하면_거부() {
        Guest existing = Guest.create(NAME, EMAIL, PHONE, FIXED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.change(new ChangeGuestCommand(
            existing.id().asString(), "Alice", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("firstName and lastName");
    }

    @Test
    @DisplayName("change: 존재하지 않는 id 는 GuestNotFoundException")
    void change_없는_id_거부() {
        GuestId id = GuestId.newId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.change(new ChangeGuestCommand(
            id.asString(), "Alice", "Kim", null, null)))
            .isInstanceOf(GuestNotFoundException.class);
    }

    @Test
    @DisplayName("findById: Result 변환")
    void findById_Result_변환() {
        Guest existing = Guest.create(NAME, EMAIL, PHONE, FIXED);
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));

        GuestResult result = service.findById(existing.id().asString());

        assertThat(result.id()).isEqualTo(existing.id().asString());
        assertThat(result.email()).isEqualTo("hong@example.com");
    }

    @Test
    @DisplayName("findById: 없는 id 는 GuestNotFoundException")
    void findById_없으면_예외() {
        GuestId id = GuestId.newId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id.asString()))
            .isInstanceOf(GuestNotFoundException.class);
    }

    @Test
    @DisplayName("findAllByIds: 빈 입력은 빈 리스트 반환 + repository 호출 없음")
    void findAllByIds_빈_입력() {
        List<GuestResult> results = service.findAllByIds(List.of());

        assertThat(results).isEmpty();
        verify(repository, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("findAllByIds: 존재분만 반환 — 누락은 partial response 관례")
    void findAllByIds_존재분만_반환() {
        Guest g1 = Guest.create(NAME, EMAIL, PHONE, FIXED);
        Guest g2 = Guest.create(
            new GuestName("Alice", "Kim"),
            new Email("alice@example.com"),
            new PhoneNumber("+82-10-9999-0000"),
            FIXED);
        GuestId missing = GuestId.newId();
        when(repository.findAllByIds(any())).thenReturn(List.of(g1, g2));

        List<GuestResult> results = service.findAllByIds(List.of(
            g1.id().asString(), g2.id().asString(), missing.asString()));

        assertThat(results).hasSize(2)
            .extracting(GuestResult::email)
            .containsExactly("hong@example.com", "alice@example.com");
    }
}

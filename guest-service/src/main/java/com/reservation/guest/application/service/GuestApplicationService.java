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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Guest Aggregate Use Case. FR-G-01 등록 · FR-G-02 조회 (REST + gRPC) · FR-G-03 변경.
 *
 * <p>이메일 유일성은 Aggregate 집합 전체의 불변식이므로 본 계층이 방어한다 —
 * {@link GuestRepository#existsByEmail} 선제 검증 + DB UNIQUE 최종 방어. 동시 요청이
 * 검증 윈도우 사이에 발생하면 {@link DataIntegrityViolationException} 을
 * {@link DuplicateEmailException} 으로 통일 매핑한다 (ddd-architect H2).
 *
 * <p>이벤트 발행은 없다 — PRD §7.2 에 guest-service 발행 토픽 없음. 향후 Audit 요건
 * 진입 시 본 서비스에 Outbox 도입 예정.
 */
@Service
@RequiredArgsConstructor
public class GuestApplicationService {

    private final GuestRepository repository;
    private final Clock clock;

    @Transactional
    public GuestResult register(RegisterGuestCommand command) {
        Objects.requireNonNull(command, "command");
        GuestName name = new GuestName(command.firstName(), command.lastName());
        Email email = new Email(command.email());
        PhoneNumber phone = new PhoneNumber(command.phoneNumber());

        if (repository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Guest guest = Guest.create(name, email, phone, clock);
        try {
            return GuestResult.of(repository.save(guest));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException(email);
        }
    }

    /**
     * PATCH 부분 변경. {@code null} 필드는 변경 없음으로 해석하고, 실제 상태가 바뀐
     * 항목이 하나라도 있을 때만 저장한다 (no-op 최적화).
     */
    @Transactional
    public GuestResult change(ChangeGuestCommand command) {
        Objects.requireNonNull(command, "command");
        GuestId id = GuestId.of(command.guestId());
        Guest guest = repository.findById(id)
            .orElseThrow(() -> new GuestNotFoundException(id));

        boolean changed = false;

        if (command.firstName() != null || command.lastName() != null) {
            if (command.firstName() == null || command.lastName() == null) {
                throw new IllegalArgumentException(
                    "firstName and lastName must be provided together");
            }
            changed |= guest.changeName(new GuestName(command.firstName(), command.lastName()), clock);
        }

        if (command.email() != null) {
            Email newEmail = new Email(command.email());
            if (!guest.email().equals(newEmail) && repository.existsByEmail(newEmail)) {
                throw new DuplicateEmailException(newEmail);
            }
            changed |= guest.changeEmail(newEmail, clock);
        }

        if (command.phoneNumber() != null) {
            changed |= guest.changePhoneNumber(new PhoneNumber(command.phoneNumber()), clock);
        }

        if (!changed) {
            return GuestResult.of(guest);
        }

        try {
            return GuestResult.of(repository.save(guest));
        } catch (DataIntegrityViolationException e) {
            // email 변경 경쟁 상태 방어선.
            throw new DuplicateEmailException(guest.email());
        }
    }

    @Transactional(readOnly = true)
    public GuestResult findById(String guestId) {
        Objects.requireNonNull(guestId, "guestId");
        GuestId id = GuestId.of(guestId);
        return GuestResult.of(repository.findById(id)
            .orElseThrow(() -> new GuestNotFoundException(id)));
    }

    /**
     * gRPC {@code BatchGetGuests} 전용. 존재하는 Guest 만 반환한다 — 누락은 호출자가
     * 요청 id 집합과의 차집합으로 감지한다 (partial response 관례).
     */
    @Transactional(readOnly = true)
    public List<GuestResult> findAllByIds(Collection<String> guestIds) {
        Objects.requireNonNull(guestIds, "guestIds");
        if (guestIds.isEmpty()) {
            return List.of();
        }
        List<GuestId> ids = guestIds.stream().map(GuestId::of).toList();
        return repository.findAllByIds(ids).stream()
            .map(GuestResult::of)
            .toList();
    }
}

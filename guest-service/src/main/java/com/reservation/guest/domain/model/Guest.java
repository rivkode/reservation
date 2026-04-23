package com.reservation.guest.domain.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Guest Aggregate Root — 투숙객 마스터 엔티티 (FR-G-01/02/03).
 *
 * <p>단일 AR. Address · VisitHistory 등 향후 확장 필드는 별도 AR 로 분리한다
 * (Giant Aggregate 회피 — ddd-architect M3). {@code GuestName · Email · PhoneNumber}
 * 는 Value Object 로 캡슐화되어 AR 내부에서 형식 검증 책임을 분담한다.
 *
 * <p>불변식:
 * <ul>
 *   <li>모든 필드 non-null</li>
 *   <li>createdAt ≤ updatedAt</li>
 * </ul>
 *
 * <p>이메일 유일성은 Aggregate 집합 전체의 불변식이므로 <b>Application Service</b> 가
 * {@code GuestRepository.existsByEmail} + DB UNIQUE 제약으로 이중 방어한다
 * (ddd-architect H2). AR 내부에는 유일성 판단 정보가 없다.
 *
 * <p>도메인 행위:
 * <ul>
 *   <li>{@link #create} · {@link #restore} — 생성/복원</li>
 *   <li>{@link #changeName} · {@link #changeEmail} · {@link #changePhoneNumber}
 *       — 각 필드별 변경. 동일 값이면 {@code false} 를 반환해 호출자가 no-op 임을 알고
 *       save · 이벤트 스킵할 수 있게 한다 (RoomTypeRate 와 동일 패턴).</li>
 * </ul>
 *
 * <p>이벤트 발행은 본 PR 범위 외 (PRD §7.2 에 guest-service 발행 토픽 없음).
 * 향후 GDPR / Audit 요건 진입 시 {@code GuestRegistered}/{@code GuestContactChanged}
 * 를 contracts 에 추가할 수 있다.
 */
public class Guest {

    private final GuestId id;
    private GuestName name;
    private Email email;
    private PhoneNumber phoneNumber;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Guest(GuestId id,
                  GuestName name,
                  Email email,
                  PhoneNumber phoneNumber,
                  long version,
                  Instant createdAt,
                  Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** 신규 투숙객 등록. id 는 내부에서 UUID v7 로 발급한다. */
    public static Guest create(GuestName name,
                               Email email,
                               PhoneNumber phoneNumber,
                               Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = Instant.now(clock);
        return new Guest(GuestId.newId(), name, email, phoneNumber, 0L, now, now);
    }

    /** 영속 저장소에서 복원. 외부(Mapper) 가 보유 중인 상태 그대로 주입. */
    public static Guest restore(GuestId id,
                                GuestName name,
                                Email email,
                                PhoneNumber phoneNumber,
                                long version,
                                Instant createdAt,
                                Instant updatedAt) {
        return new Guest(id, name, email, phoneNumber, version, createdAt, updatedAt);
    }

    /**
     * 이름 변경. 동일 값이면 상태를 바꾸지 않고 {@code false} 를 반환한다.
     * 호출자(Application Service) 는 {@code true} 인 경우에만 save · 이벤트 발행을 수행.
     */
    public boolean changeName(GuestName newName, Clock clock) {
        Objects.requireNonNull(newName, "newName");
        Objects.requireNonNull(clock, "clock");
        if (this.name.equals(newName)) {
            return false;
        }
        this.name = newName;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    /**
     * 이메일 변경. Application Service 는 호출 <b>전</b> 에 중복 검사를 수행해야 한다
     * (ddd-architect H2).
     */
    public boolean changeEmail(Email newEmail, Clock clock) {
        Objects.requireNonNull(newEmail, "newEmail");
        Objects.requireNonNull(clock, "clock");
        if (this.email.equals(newEmail)) {
            return false;
        }
        this.email = newEmail;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    /** 전화번호 변경. 동일 값(E.164 정규화 결과 기준)이면 no-op. */
    public boolean changePhoneNumber(PhoneNumber newPhoneNumber, Clock clock) {
        Objects.requireNonNull(newPhoneNumber, "newPhoneNumber");
        Objects.requireNonNull(clock, "clock");
        if (this.phoneNumber.equals(newPhoneNumber)) {
            return false;
        }
        this.phoneNumber = newPhoneNumber;
        this.updatedAt = Instant.now(clock);
        return true;
    }

    public GuestId id() {
        return id;
    }

    public GuestName name() {
        return name;
    }

    public Email email() {
        return email;
    }

    public PhoneNumber phoneNumber() {
        return phoneNumber;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

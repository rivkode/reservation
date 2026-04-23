package com.reservation.guest.domain.exception;

import com.reservation.guest.domain.model.Email;

/**
 * 이메일 유일성(Aggregate 집합 불변식) 위반. Application Service 의 선제 검사와
 * DB UNIQUE 제약이 함께 방어하며, 양쪽 어디에서든 이 예외로 표준화해 presentation
 * 은 HTTP 409, gRPC 는 {@code Status.ALREADY_EXISTS} 로 매핑한다.
 */
public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(Email email) {
        super("Guest email already exists: " + email.value());
        this.email = email.value();
    }

    public String email() {
        return email;
    }
}

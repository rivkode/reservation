package com.reservation.reservation.domain.exception;

/**
 * {@code Money.add(Money)} 호출 시 두 통화가 다를 때 발생하는 도메인 예외.
 *
 * <p>예약 생성 흐름에서 rate-service 가 N 일치 견적을 동일 통화로 반환한다는 가정을
 * VO 가 강제한다. 위반은 외부 SoT(rate-service) 의 결과 정합성 오류이므로 Application
 * 계층은 본 예외를 catch 해 5xx 로 매핑한다 — 클라이언트가 재시도해도 즉시 회복되지
 * 않는 시스템 오류.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String left, String right) {
        super("currency mismatch: " + left + " vs " + right);
    }
}

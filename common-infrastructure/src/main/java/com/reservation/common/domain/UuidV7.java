package com.reservation.common.domain;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/**
 * UUID v7 생성기. {@code com.fasterxml.uuid:java-uuid-generator} 의
 * {@link Generators#timeBasedEpochGenerator()} 를 얇게 래핑한다.
 *
 * <p>RFC 9562 §5.7 의 v7 사양(48-bit unix epoch millisecond timestamp + version
 * nibble 7 + variant bits + sub-millisecond monotonic counter) 을 검증된 라이브러리에
 * 위임해 다음을 보장한다:
 *
 * <ul>
 *   <li><strong>same-millisecond 내 lexicographic 순서 보장</strong> —
 *       sub-millisecond counter 로 동일 ms 에서 생성된 UUID 들의 문자열 정렬이
 *       실제 생성 순서와 일치한다. DB 클러스터드 인덱스 삽입 성능과 이벤트
 *       순서 추적에 필수.</li>
 *   <li><strong>clock-goes-backwards 방어</strong> — NTP 보정 · VM 일시정지 등으로
 *       시스템 시계가 뒤로 이동해도 last-seen timestamp 를 유지해 단조 증가 강제.</li>
 * </ul>
 *
 * <p>PRD §11.2 가 {@code reservationId} 를 UUID v7 로 규정하며, 본 프로젝트는 전
 * Aggregate ID (Hotel · Room · Rate · Guest · Reservation · Billing) 에 동일 규약을
 * 적용한다. DB 저장 형식은 {@code BINARY(16)} —
 * {@link com.reservation.common.persistence.UuidBinaryConverter} 참조.
 *
 * <p>테스트에서 Clock 을 고정하고 싶다면 JUG 의 생성자에 커스텀 {@code UUIDClock} 을
 * 주입한 별도 generator 인스턴스를 만들어 사용한다. 본 유틸은 프로덕션 단일 진입점이다.
 */
public final class UuidV7 {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidV7() {
    }

    /** UUID v7 를 생성한다 (monotonic · clock-backwards 방어 포함). */
    public static UUID create() {
        return GENERATOR.generate();
    }
}

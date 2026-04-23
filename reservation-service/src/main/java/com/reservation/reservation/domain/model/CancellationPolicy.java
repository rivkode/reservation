package com.reservation.reservation.domain.model;

import java.time.Instant;

/**
 * 예약 취소 시점의 환불률 / 위약금을 결정하는 도메인 정책 (Strategy).
 *
 * <p>PRD §13 Q3 가 명시한 "{@code CancellationPolicy} VO 로 추상화해 향후 72h/24h/당일
 * 차등 확장 가능" 의 추상화 지점. 본 PR 의 구현체는 {@code TwentyFourHourCancellationPolicy}
 * 단 하나 — 체크인 24h 전이면 100% 환불, 이내면 0%.
 *
 * <p>시그니처는 정책이 미래에 가질 분기 입력을 모두 받도록 설계한다 — Reservation 전체
 * 객체 + 취소 사유 + 취소 시각. 현재 정책이 stayPeriod.checkIn 만 보더라도 시그니처를
 * 좁게 시작하면 정책 확장 시마다 모든 호출자 변경이 필요하다 (ddd-architect High-1).
 *
 * <p>구현체는 Spring Bean 으로 등록되어 Application Service 가 DI 로 주입받는다 — 도메인
 * 자체는 Spring 어노테이션을 모르고 인터페이스만 노출.
 */
public interface CancellationPolicy {

    CancellationOutcome decide(Reservation reservation, CancellationReason reason, Instant cancelledAt);
}

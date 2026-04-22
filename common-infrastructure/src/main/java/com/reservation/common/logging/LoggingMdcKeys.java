package com.reservation.common.logging;

/**
 * 로그 MDC (Mapped Diagnostic Context) 표준 키.
 *
 * <p>모든 서비스 로그는 아래 키로 컨텍스트를 enrich 한다. gRPC Metadata ·
 * Kafka record header · HTTP header 에서도 <strong>같은 키 명</strong> 으로
 * 전파해 분산 시스템 전역에서 한 요청의 흐름을 추적할 수 있어야 한다.
 *
 * <p>실제 전파 구현(HTTP Filter · gRPC ServerInterceptor · Kafka ProducerInterceptor)
 * 은 이후 PR 에서 단계적으로 추가한다. 본 클래스는 문자열 키를 상수화해
 * 오타 · 드리프트를 방지하는 용도.
 */
public final class LoggingMdcKeys {

    /** 분산 트레이싱의 trace 식별자 (upstream 전파 또는 자체 생성). */
    public static final String TRACE_ID = "traceId";

    /** 요청 단위 상관관계 식별자. 비즈니스 요청의 cross-service 추적에 사용. */
    public static final String CORRELATION_ID = "correlationId";

    /** 처리 중인 도메인 이벤트 ID. Kafka consumer 측 멱등성 로그에 필수. */
    public static final String EVENT_ID = "eventId";

    private LoggingMdcKeys() {
    }
}

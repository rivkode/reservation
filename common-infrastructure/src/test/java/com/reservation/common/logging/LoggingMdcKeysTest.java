package com.reservation.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingMdcKeysTest {

    @Test
    @DisplayName("표준 MDC 키가 약속된 문자열을 노출한다 — 전파 규약 드리프트 감지")
    void standardKeysAreStable() {
        assertThat(LoggingMdcKeys.TRACE_ID).isEqualTo("traceId");
        assertThat(LoggingMdcKeys.CORRELATION_ID).isEqualTo("correlationId");
        assertThat(LoggingMdcKeys.EVENT_ID).isEqualTo("eventId");
    }
}

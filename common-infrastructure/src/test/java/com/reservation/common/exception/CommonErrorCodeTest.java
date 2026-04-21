package com.reservation.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CommonErrorCodeTest {

    @ParameterizedTest(name = "{0} → HTTP {1}")
    @CsvSource({
        "VALIDATION_FAILED, 400",
        "RESOURCE_NOT_FOUND, 404",
        "CONFLICT, 409",
        "UNAUTHENTICATED, 401",
        "FORBIDDEN, 403",
        "EXTERNAL_SERVICE_UNAVAILABLE, 503",
        "INTERNAL_ERROR, 500"
    })
    @DisplayName("각 CommonErrorCode 는 의도된 기본 HTTP status 를 노출한다")
    void defaultStatusMatchesContract(CommonErrorCode code, int expectedStatus) {
        assertThat(code.defaultStatus()).isEqualTo(expectedStatus);
    }

    // 새 에러 코드가 추가됐는데 위 @CsvSource 에 등록을 잊으면 매핑이 검증되지 않아도
    // 전체 빌드가 통과해 버린다. size assertion 으로 드리프트를 감지한다.
    @Test
    @DisplayName("defaultStatusMatchesContract 가 enum 의 모든 값을 커버한다")
    void parameterizedCoversAllEnumValues() {
        assertThat(CommonErrorCode.values()).hasSize(7);
    }
}

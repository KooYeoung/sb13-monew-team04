package com.codeit.sb13.monew.global;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MonewHttpHeaders 단위 테스트")
class MonewHttpHeadersTest {

    @Test
    @DisplayName("요청 사용자 ID 헤더명을 제공한다")
    void providesRequestUserIdHeaderName() {
        assertThat(MonewHttpHeaders.REQUEST_USER_ID).isEqualTo("Monew-Request-User-ID");
    }
}

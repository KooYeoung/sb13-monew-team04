package com.codeit.sb13.monew.activity.outbox.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.outbox.OutboxRetryPolicyException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    private final OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy();

    @Test
    @DisplayName("실패 횟수에 따라 1분, 5분, 15분, 1시간 뒤에 재시도한다")
    void retryDelays() {
        assertThat(retryPolicy.delayAfter(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retryPolicy.delayAfter(1)).isEqualTo(Duration.ofMinutes(5));
        assertThat(retryPolicy.delayAfter(2)).isEqualTo(Duration.ofMinutes(15));
        assertThat(retryPolicy.delayAfter(3)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("다섯 번째 실패부터 재시도 한도를 초과한다")
    void exhaustedOnFifthFailure() {
        assertThat(retryPolicy.exhaustedAfter(3)).isFalse();
        assertThat(retryPolicy.exhaustedAfter(4)).isTrue();
        assertThatThrownBy(() -> retryPolicy.delayAfter(4))
                .isInstanceOfSatisfying(OutboxRetryPolicyException.class, exception -> {
                    assertThat(exception.getApiErrorCode())
                            .isEqualTo(ApiErrorCode.OUTBOX_RETRY_POLICY_INVALID);
                    assertThat(exception.getDetails())
                            .containsEntry("currentRetryCount", 4)
                            .containsEntry("maxRetryCount", 5);
                });
    }
}

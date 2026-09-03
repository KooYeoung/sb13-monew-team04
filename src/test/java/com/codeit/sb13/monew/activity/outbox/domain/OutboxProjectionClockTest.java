package com.codeit.sb13.monew.activity.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxProjectionClockTest {

    @Test
    @DisplayName("singleton id가 1이면 다음 BIGINT projection version을 발급한다")
    void singletonClockAllocatesNextVersion() {
        OutboxProjectionClock clock = new OutboxProjectionClock();
        ReflectionTestUtils.setField(clock, "id", OutboxProjectionClock.SINGLETON_ID);
        ReflectionTestUtils.setField(clock, "currentVersion", (long) Integer.MAX_VALUE);

        assertThat(clock.nextVersion()).isEqualTo((long) Integer.MAX_VALUE + 1);
    }

    @Test
    @DisplayName("singleton id가 1이 아니면 애플리케이션에서 버전 발급을 거부한다")
    void invalidSingletonIdIsRejected() {
        OutboxProjectionClock clock = new OutboxProjectionClock();
        ReflectionTestUtils.setField(clock, "id", 2L);

        assertThatThrownBy(clock::nextVersion)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
    }
}

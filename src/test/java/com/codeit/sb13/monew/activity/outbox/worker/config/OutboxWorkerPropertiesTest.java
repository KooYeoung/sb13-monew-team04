package com.codeit.sb13.monew.activity.outbox.worker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.global.exception.outbox.OutboxWorkerConfigurationException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

class OutboxWorkerPropertiesTest {

    @Test
    @DisplayName("heartbeat 간격은 claim lease보다 짧아야 한다")
    void heartbeatMustBeShorterThanLease() {
        assertInvalidConfiguration(
                "heartbeatInterval",
                "PT1M",
                "claimLease보다 짧아야 합니다.",
                () -> new OutboxWorkerProperties(
                        true,
                        1000,
                        100,
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(1)
                )
        );
    }

    @Test
    @DisplayName("polling 간격은 0보다 커야 한다")
    void fixedDelayMustBePositive() {
        assertInvalidConfiguration(
                "fixedDelayMs",
                "0",
                "0보다 커야 합니다.",
                () -> new OutboxWorkerProperties(
                        true,
                        0,
                        100,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1)
                )
        );
    }

    @Test
    @DisplayName("batch 크기는 0보다 커야 한다")
    void batchSizeMustBePositive() {
        assertInvalidConfiguration(
                "batchSize",
                "0",
                "0보다 커야 합니다.",
                () -> new OutboxWorkerProperties(
                        true,
                        1000,
                        0,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(1)
                )
        );
    }

    @Test
    @DisplayName("claim lease는 null일 수 없다")
    void claimLeaseMustNotBeNull() {
        assertInvalidConfiguration(
                "claimLease",
                "null",
                "0보다 커야 합니다.",
                () -> new OutboxWorkerProperties(
                        true,
                        1000,
                        100,
                        null,
                        Duration.ofMinutes(1)
                )
        );
    }

    @Test
    @DisplayName("heartbeat 간격은 0보다 커야 한다")
    void heartbeatIntervalMustBePositive() {
        assertInvalidConfiguration(
                "heartbeatInterval",
                "PT0S",
                "0보다 커야 합니다.",
                () -> new OutboxWorkerProperties(
                        true,
                        1000,
                        100,
                        Duration.ofMinutes(5),
                        Duration.ZERO
                )
        );
    }

    private void assertInvalidConfiguration(
            String property,
            String rejectedValue,
            String reason,
            ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        OutboxWorkerConfigurationException.class,
                        exception -> assertThat(exception.getDetails())
                                .containsEntry("property", property)
                                .containsEntry("rejectedValue", rejectedValue)
                                .containsEntry("reason", reason)
                );
    }
}

package com.codeit.sb13.monew.activity.mongo.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.sb13.monew.global.exception.readmodel.ReadModelBackfillConfigurationException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadModelBackfillPropertiesTest {

    @Test
    @DisplayName("초기 투영을 활성화하면 run-id가 필수다")
    void enabledBackfillRequiresRunId() {
        assertThatThrownBy(() -> properties(true, null, 1000, 100))
                .isInstanceOfSatisfying(
                        ReadModelBackfillConfigurationException.class,
                        exception -> assertThat(exception.getDetails())
                                .containsEntry("property", "runId")
                );
    }

    @Test
    @DisplayName("초기 투영을 비활성화하면 run-id를 생략할 수 있다")
    void disabledBackfillAllowsMissingRunId() {
        ReadModelBackfillProperties properties = properties(false, null, 1000, 100);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.runId()).isNull();
    }

    @Test
    @DisplayName("batch 크기와 polling 간격은 양수여야 한다")
    void batchAndDelayMustBePositive() {
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> properties(true, runId, 0, 100))
                .isInstanceOf(ReadModelBackfillConfigurationException.class);
        assertThatThrownBy(() -> properties(true, runId, 1000, 0))
                .isInstanceOf(ReadModelBackfillConfigurationException.class);
    }

    private ReadModelBackfillProperties properties(
            boolean enabled,
            UUID runId,
            long delay,
            int batchSize
    ) {
        return new ReadModelBackfillProperties(
                enabled,
                runId,
                delay,
                batchSize,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1)
        );
    }
}

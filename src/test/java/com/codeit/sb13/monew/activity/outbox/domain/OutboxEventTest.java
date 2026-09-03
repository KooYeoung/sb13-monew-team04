package com.codeit.sb13.monew.activity.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.outbox.OutboxEventStateTransitionException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

class OutboxEventTest {

    @Test
    @DisplayName("FAILED 상태에서 다시 실패하면 재시도 횟수와 오류 정보를 갱신한다")
    void markFailedAgain() {
        OutboxEvent event = createPendingEvent();
        event.markFailed("first failure", LocalDateTime.of(2026, 9, 2, 10, 1));

        LocalDateTime nextRetryAt = LocalDateTime.of(2026, 9, 2, 10, 5);
        event.markFailed("second failure", nextRetryAt);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(event.getProcessedAt()).isNull();
        assertThat(event.getLastError()).isEqualTo("second failure");
    }

    @Test
    @DisplayName("PROCESSED 상태에서는 모든 후속 상태 전이를 거부하고 필드를 유지한다")
    void rejectTransitionsFromProcessed() {
        OutboxEvent event = createPendingEvent();
        LocalDateTime processedAt = LocalDateTime.of(2026, 9, 2, 10, 2);
        event.markProcessed(processedAt);

        assertRejectedTransition(
                event,
                OutboxEventStatus.PROCESSED,
                OutboxEventStatus.PROCESSED,
                () -> event.markProcessed(processedAt.plusMinutes(1))
        );
        assertRejectedTransition(
                event,
                OutboxEventStatus.PROCESSED,
                OutboxEventStatus.FAILED,
                () -> event.markFailed("late failure", processedAt.plusMinutes(2))
        );
        assertRejectedTransition(
                event,
                OutboxEventStatus.PROCESSED,
                OutboxEventStatus.DEAD_LETTER,
                () -> event.markDeadLetter("late dead letter")
        );

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getProcessedAt()).isEqualTo(processedAt);
        assertThat(event.getNextRetryAt()).isNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    @DisplayName("DEAD_LETTER 상태에서는 모든 후속 상태 전이를 거부하고 필드를 유지한다")
    void rejectTransitionsFromDeadLetter() {
        OutboxEvent event = createPendingEvent();
        LocalDateTime nextRetryAt = LocalDateTime.of(2026, 9, 2, 10, 1);
        event.markFailed("first failure", nextRetryAt);
        event.markDeadLetter("retry exhausted");

        assertRejectedTransition(
                event,
                OutboxEventStatus.DEAD_LETTER,
                OutboxEventStatus.PROCESSED,
                () -> event.markProcessed(nextRetryAt.plusMinutes(1))
        );
        assertRejectedTransition(
                event,
                OutboxEventStatus.DEAD_LETTER,
                OutboxEventStatus.FAILED,
                () -> event.markFailed("late failure", nextRetryAt.plusMinutes(2))
        );
        assertRejectedTransition(
                event,
                OutboxEventStatus.DEAD_LETTER,
                OutboxEventStatus.DEAD_LETTER,
                () -> event.markDeadLetter("late dead letter")
        );

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD_LETTER);
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getProcessedAt()).isNull();
        assertThat(event.getNextRetryAt()).isNull();
        assertThat(event.getLastError()).isEqualTo("retry exhausted");
    }

    private void assertRejectedTransition(
            OutboxEvent event,
            OutboxEventStatus currentStatus,
            OutboxEventStatus targetStatus,
            Runnable transition
    ) {
        Throwable thrown = catchThrowable(transition::run);
        assertThat(thrown).isInstanceOf(OutboxEventStateTransitionException.class);
        OutboxEventStateTransitionException exception =
                (OutboxEventStateTransitionException) thrown;

        assertThat(exception.getApiErrorCode())
                .isEqualTo(ApiErrorCode.OUTBOX_STATE_TRANSITION_INVALID);
        assertThat(exception.getDetails()).isEqualTo(Map.of(
                "currentStatus", currentStatus.name(),
                "targetStatus", targetStatus.name()
        ));
        assertThat(event.getStatus()).isEqualTo(currentStatus);
    }

    private OutboxEvent createPendingEvent() {
        return OutboxEvent.createPending(
                OutboxEventType.ARTICLE_VIEWED,
                OutboxAggregateType.ARTICLE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JsonNodeFactory.instance.objectNode().put("viewed", true),
                LocalDateTime.of(2026, 9, 2, 10, 0),
                1L
        );
    }
}

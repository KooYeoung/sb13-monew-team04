package com.codeit.sb13.monew.activity.mongo.backfill;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import com.codeit.sb13.monew.activity.outbox.worker.ProjectionCommand;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 기존 RDB 활동 row를 동일한 Outbox projection 흐름으로 전달하는 일회성 내부 명령이다.
 *
 * @param sourceRowId checkpoint cursor로 사용하는 원본 활동 row ID
 */
public record InitialProjectionEvent(
        UUID sourceRowId,
        OutboxEventType eventType,
        OutboxAggregateType aggregateType,
        UUID aggregateId,
        UUID actorUserId,
        OutboxEventPayload payload,
        long projectionVersion,
        LocalDateTime occurredAt
) implements ProjectionCommand {
}

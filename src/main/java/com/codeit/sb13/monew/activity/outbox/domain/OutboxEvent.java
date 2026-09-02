package com.codeit.sb13.monew.activity.outbox.domain;

import com.codeit.sb13.monew.global.domain.UpdatedAtEntity;
import com.codeit.sb13.monew.global.exception.outbox.OutboxEventStateTransitionException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends UpdatedAtEntity {

    @Column(name = "event_type", nullable = false, length = 80)
    @Enumerated(EnumType.STRING)
    private OutboxEventType eventType;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private OutboxAggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    private JsonNode payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    private OutboxEvent(
            OutboxEventType eventType,
            OutboxAggregateType aggregateType,
            UUID aggregateId,
            UUID actorUserId,
            JsonNode payloadJson,
            LocalDateTime occurredAt
    ) {
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.actorUserId = actorUserId;
        this.payloadJson = payloadJson;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.occurredAt = occurredAt;
    }

    public static OutboxEvent createPending(
            OutboxEventType eventType,
            OutboxAggregateType aggregateType,
            UUID aggregateId,
            UUID actorUserId,
            JsonNode payloadJson,
            LocalDateTime occurredAt
    ) {
        return new OutboxEvent(
                eventType,
                aggregateType,
                aggregateId,
                actorUserId,
                payloadJson,
                occurredAt
        );
    }

    public void markProcessed(LocalDateTime processedAt) {
        validateTransitionTo(OutboxEventStatus.PROCESSED);
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = processedAt;
        this.nextRetryAt = null;
        this.lastError = null;
    }

    public void markFailed(String lastError, LocalDateTime nextRetryAt) {
        validateTransitionTo(OutboxEventStatus.FAILED);
        this.status = OutboxEventStatus.FAILED;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.processedAt = null;
        this.lastError = lastError;
    }

    public void markDeadLetter(String lastError) {
        validateTransitionTo(OutboxEventStatus.DEAD_LETTER);
        this.status = OutboxEventStatus.DEAD_LETTER;
        this.retryCount++;
        this.nextRetryAt = null;
        this.processedAt = null;
        this.lastError = lastError;
    }

    private void validateTransitionTo(OutboxEventStatus targetStatus) {
        if (status == OutboxEventStatus.PENDING || status == OutboxEventStatus.FAILED) {
            return;
        }
        throw new OutboxEventStateTransitionException(status.name(), targetStatus.name());
    }
}

package com.codeit.sb13.monew.activity.outbox.domain;

import com.codeit.sb13.monew.global.domain.UpdatedAtEntity;
import com.codeit.sb13.monew.global.exception.outbox.OutboxEventStateTransitionException;
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
import tools.jackson.databind.JsonNode;

/**
 * RDB 원본 변경과 같은 트랜잭션에 저장되는 Outbox 이벤트 envelope다.
 *
 * <p>payload는 MongoDB 문서 전체가 아니라 변경 사실과 처리 대상 식별자를 담는다.
 * worker는 커밋된 이벤트를 claim한 뒤 RDB 현재 상태를 다시 읽어 MongoDB Read
 * Model을 갱신한다. 처리 상태와 retry 정보는 이벤트 수명주기를, claim 필드는
 * 다중 worker 사이의 임시 소유권과 lease를 나타낸다.</p>
 */
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

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "claim_until")
    private LocalDateTime claimUntil;

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

    /**
     * 최초 처리 대기 상태의 이벤트를 생성한다.
     *
     * @param eventType worker 처리 방식을 결정하는 이벤트 종류
     * @param aggregateType 원본 도메인 종류
     * @param aggregateId 원본 엔티티 식별자
     * @param actorUserId 이벤트를 발생시킨 사용자 식별자, 시스템 이벤트이면 {@code null}
     * @param payloadJson 타입별 payload를 직렬화한 JSON
     * @param occurredAt 도메인 변경이 발생한 시각
     * @return {@link OutboxEventStatus#PENDING} 상태의 새 이벤트
     */
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

    /**
     * projection 반영을 완료 상태로 전환하고 retry 및 claim 정보를 정리한다.
     *
     * @param processedAt MongoDB 반영을 완료한 시각
     * @throws OutboxEventStateTransitionException 현재 상태가 전이 가능한 상태가 아닌 경우
     */
    public void markProcessed(LocalDateTime processedAt) {
        validateTransitionTo(OutboxEventStatus.PROCESSED);
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = processedAt;
        this.nextRetryAt = null;
        this.lastError = null;
        clearClaim();
    }

    /**
     * 처리 실패를 기록하고 다음 재시도 시각을 설정하며 현재 claim을 해제한다.
     *
     * @param lastError 마지막 실패 원인
     * @param nextRetryAt 다시 처리할 수 있는 시각
     * @throws OutboxEventStateTransitionException 현재 상태가 전이 가능한 상태가 아닌 경우
     */
    public void markFailed(String lastError, LocalDateTime nextRetryAt) {
        validateTransitionTo(OutboxEventStatus.FAILED);
        this.status = OutboxEventStatus.FAILED;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.processedAt = null;
        this.lastError = lastError;
        clearClaim();
    }

    /**
     * 자동 재시도를 종료하고 Dead Letter 상태로 전환한다.
     *
     * @param lastError 마지막 실패 원인
     * @throws OutboxEventStateTransitionException 현재 상태가 전이 가능한 상태가 아닌 경우
     */
    public void markDeadLetter(String lastError) {
        validateTransitionTo(OutboxEventStatus.DEAD_LETTER);
        this.status = OutboxEventStatus.DEAD_LETTER;
        this.retryCount++;
        this.nextRetryAt = null;
        this.processedAt = null;
        this.lastError = lastError;
        clearClaim();
    }

    private void clearClaim() {
        this.claimId = null;
        this.claimedAt = null;
        this.claimUntil = null;
    }

    private void validateTransitionTo(OutboxEventStatus targetStatus) {
        if (status == OutboxEventStatus.PENDING || status == OutboxEventStatus.FAILED) {
            return;
        }
        throw new OutboxEventStateTransitionException(status.name(), targetStatus.name());
    }
}

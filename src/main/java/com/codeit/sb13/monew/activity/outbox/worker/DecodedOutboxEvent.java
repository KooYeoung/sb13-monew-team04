package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 저장용 JSON payload를 실제 record로 복원한 worker 내부 이벤트다.
 *
 * <p>RDB source batch 조회와 MongoDB projection 단계가 Jackson이나 JPA 엔티티에
 * 의존하지 않도록 필요한 envelope 값을 불변 형태로 전달한다.</p>
 *
 * @param id Outbox 이벤트 식별자
 * @param eventType 이벤트 종류
 * @param aggregateType 원본 도메인 종류
 * @param aggregateId 원본 엔티티 식별자
 * @param actorUserId 이벤트를 발생시킨 사용자 식별자, 시스템 이벤트이면 {@code null}
 * @param payload 이벤트 타입에 맞게 복원된 payload record
 * @param projectionVersion MongoDB CAS에 사용하는 전역 commit 순서 버전
 * @param retryCount 현재까지 기록된 처리 실패 횟수
 * @param occurredAt 도메인 변경 발생 시각
 * @param createdAt Outbox row 생성 시각
 */
public record DecodedOutboxEvent(
        UUID id,
        OutboxEventType eventType,
        OutboxAggregateType aggregateType,
        UUID aggregateId,
        UUID actorUserId,
        OutboxEventPayload payload,
        long projectionVersion,
        int retryCount,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
) {
}

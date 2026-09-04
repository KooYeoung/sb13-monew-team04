package com.codeit.sb13.monew.activity.outbox.worker;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.OutboxEventPayload;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RDB 현재 상태를 MongoDB Read Model에 반영하기 위한 공통 내부 명령이다.
 *
 * <p>실시간 Outbox 이벤트와 초기 투영은 이 계약을 함께 사용한다. 따라서 두 경로 모두
 * 같은 source reader와 projection handler를 거치며 payload의 과거 표시값이 아니라
 * 처리 시점의 RDB 상태와 projection version CAS를 기준으로 수렴한다.</p>
 */
public interface ProjectionCommand {

    OutboxEventType eventType();

    OutboxAggregateType aggregateType();

    UUID aggregateId();

    UUID actorUserId();

    OutboxEventPayload payload();

    long projectionVersion();

    LocalDateTime occurredAt();
}

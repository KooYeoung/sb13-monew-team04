package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

/**
 * 관심사 변경 또는 구독 관계 이벤트의 payload다.
 *
 * @param action 관심사나 구독 관계에 발생한 동작
 * @param impact 삭제 전에 수집한 구독 activity 영향 범위
 */
public record InterestOutboxPayload(
        OutboxEventAction action,
        ProjectionImpact impact
) implements OutboxEventPayload {

    public InterestOutboxPayload {
        impact = impact == null ? ProjectionImpact.EMPTY : impact;
    }

    public InterestOutboxPayload(OutboxEventAction action) {
        this(action, ProjectionImpact.EMPTY);
    }
}

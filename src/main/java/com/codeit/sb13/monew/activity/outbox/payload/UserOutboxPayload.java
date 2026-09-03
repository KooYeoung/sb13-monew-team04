package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

/**
 * 사용자 닉네임 변경 또는 논리삭제 이벤트의 payload다.
 *
 * @param action 사용자에게 발생한 동작
 * @param impact 닉네임 변경 또는 삭제 전에 수집한 사용자 projection 영향 범위
 */
public record UserOutboxPayload(
        OutboxEventAction action,
        ProjectionImpact impact
) implements OutboxEventPayload {

    public UserOutboxPayload {
        impact = impact == null ? ProjectionImpact.EMPTY : impact;
    }

    public UserOutboxPayload(OutboxEventAction action) {
        this(action, ProjectionImpact.EMPTY);
    }
}

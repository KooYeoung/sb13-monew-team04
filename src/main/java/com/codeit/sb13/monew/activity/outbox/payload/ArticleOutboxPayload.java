package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;

/**
 * 기사 변경 또는 조회 활동 이벤트의 payload다.
 *
 * @param action 기사에 발생한 동작
 * @param impact 삭제 전에 수집한 activity와 자식 댓글 snapshot 영향 범위
 */
public record ArticleOutboxPayload(
        OutboxEventAction action,
        ProjectionImpact impact
) implements OutboxEventPayload {

    public ArticleOutboxPayload {
        impact = impact == null ? ProjectionImpact.EMPTY : impact;
    }

    public ArticleOutboxPayload(OutboxEventAction action) {
        this(action, ProjectionImpact.EMPTY);
    }
}

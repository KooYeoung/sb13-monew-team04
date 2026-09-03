package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import java.util.UUID;

/**
 * 댓글 작성·수정·삭제 이벤트의 payload다.
 *
 * <p>댓글 식별자는 Outbox 공통 {@code aggregateId}에 저장되므로, 부모 기사에
 * count 또는 visibility 변경을 전파하는 데 필요한 기사 식별자만 함께 보관한다.</p>
 *
 * @param articleId 댓글이 속한 기사 식별자
 * @param action 댓글에 발생한 동작
 * @param impact 삭제 전에 수집한 댓글 작성·좋아요 activity 영향 범위
 */
public record CommentOutboxPayload(
        UUID articleId,
        OutboxEventAction action,
        ProjectionImpact impact
) implements OutboxEventPayload {

    public CommentOutboxPayload {
        impact = impact == null ? ProjectionImpact.EMPTY : impact;
    }

    public CommentOutboxPayload(UUID articleId, OutboxEventAction action) {
        this(articleId, action, ProjectionImpact.EMPTY);
    }
}

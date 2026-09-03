package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import java.util.UUID;

/**
 * 댓글 좋아요 또는 취소 이벤트의 payload다.
 *
 * <p>댓글 식별자는 Outbox 공통 {@code aggregateId}에 저장되므로, 여기에는 부모
 * 기사 식별자만 추가로 보관한다.</p>
 *
 * @param articleId 좋아요 대상 댓글이 속한 기사 식별자
 * @param action 좋아요 관계에 발생한 동작
 */
public record CommentLikeOutboxPayload(
        UUID articleId,
        OutboxEventAction action
) implements OutboxEventPayload {
}

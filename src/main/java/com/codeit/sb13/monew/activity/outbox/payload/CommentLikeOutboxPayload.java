package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import java.util.UUID;

public record CommentLikeOutboxPayload(
        UUID articleId,
        OutboxEventAction action
) implements OutboxEventPayload {
}

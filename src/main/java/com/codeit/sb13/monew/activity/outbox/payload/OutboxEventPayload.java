package com.codeit.sb13.monew.activity.outbox.payload;

public sealed interface OutboxEventPayload permits
        ArticleOutboxPayload,
        CommentLikeOutboxPayload,
        CommentOutboxPayload,
        CountOutboxPayload,
        InterestOutboxPayload,
        UserHardDeleteOutboxPayload,
        UserOutboxPayload {
}

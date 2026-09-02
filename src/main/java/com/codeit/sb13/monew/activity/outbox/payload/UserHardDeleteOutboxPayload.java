package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record UserHardDeleteOutboxPayload(
        OutboxEventAction action,
        List<UUID> authoredCommentIds,
        List<UUID> impactedArticleIds,
        List<UUID> likedCommentIds,
        List<UUID> viewedArticleIds,
        List<UUID> subscribedInterestIds
) implements OutboxEventPayload {

    public UserHardDeleteOutboxPayload {
        authoredCommentIds = distinctCopy(authoredCommentIds);
        impactedArticleIds = distinctCopy(impactedArticleIds);
        likedCommentIds = distinctCopy(likedCommentIds);
        viewedArticleIds = distinctCopy(viewedArticleIds);
        subscribedInterestIds = distinctCopy(subscribedInterestIds);
    }

    private static List<UUID> distinctCopy(List<UUID> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }
}

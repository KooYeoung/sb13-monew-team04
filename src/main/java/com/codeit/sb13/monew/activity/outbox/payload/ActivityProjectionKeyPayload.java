package com.codeit.sb13.monew.activity.outbox.payload;

import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import java.util.UUID;

/** MongoDB activity 문서의 결정적 논리 키를 구성하는 삭제 영향 항목이다. */
public record ActivityProjectionKeyPayload(
        UUID userId,
        OutboxEventType activityEventType,
        UUID targetId
) {

    public ActivityProjectionKeyPayload {
        if (userId == null || targetId == null || !isActivityType(activityEventType)) {
            throw new IllegalArgumentException("유효한 activity projection key가 필요합니다.");
        }
    }

    private static boolean isActivityType(OutboxEventType type) {
        return type == OutboxEventType.INTEREST_SUBSCRIBED
                || type == OutboxEventType.COMMENT_WRITTEN
                || type == OutboxEventType.COMMENT_LIKED
                || type == OutboxEventType.ARTICLE_VIEWED;
    }
}

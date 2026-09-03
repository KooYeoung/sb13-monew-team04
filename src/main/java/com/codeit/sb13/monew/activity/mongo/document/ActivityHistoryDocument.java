package com.codeit.sb13.monew.activity.mongo.document;

import static com.codeit.sb13.monew.activity.mongo.MongoReadModelCollections.ACTIVITY_HISTORIES;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = ACTIVITY_HISTORIES)
public record ActivityHistoryDocument(
        @Id String id,
        String sourceActivityId,
        String userId,
        ActivityHistoryType type,
        ActivityTargetType targetType,
        String targetId,
        ActivityTargetType parentTargetType,
        String parentTargetId,
        LocalDateTime occurredAt,
        boolean visible,
        ActivityHistoryStatus status,
        ActivityTargetType hiddenByTargetType,
        String hiddenByTargetId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long projectionVersion,
        boolean tombstone
) {
}

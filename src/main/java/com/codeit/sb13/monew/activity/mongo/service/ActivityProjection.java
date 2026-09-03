package com.codeit.sb13.monew.activity.mongo.service;

import com.codeit.sb13.monew.activity.mongo.document.ActivityHistoryType;
import com.codeit.sb13.monew.activity.mongo.document.ActivityTargetType;
import java.time.LocalDateTime;
import java.util.UUID;

public record ActivityProjection(
        UUID sourceActivityId,
        UUID userId,
        ActivityHistoryType type,
        ActivityTargetType targetType,
        UUID targetId,
        ActivityTargetType parentTargetType,
        UUID parentTargetId,
        LocalDateTime occurredAt
) {
}

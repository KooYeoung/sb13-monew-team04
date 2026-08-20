package com.codeit.sb13.monew.notification.controller.dto;

import com.codeit.sb13.monew.notification.domain.ResourceType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        String content,
        UUID resourceId,
        ResourceType resourceType,
        boolean confirmed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

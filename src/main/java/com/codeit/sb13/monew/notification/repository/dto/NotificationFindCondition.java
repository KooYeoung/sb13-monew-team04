package com.codeit.sb13.monew.notification.repository.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationFindCondition(
        UUID userId,
        UUID cursorId,
        LocalDateTime after,
        int limit
) {
}

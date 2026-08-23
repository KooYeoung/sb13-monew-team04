package com.codeit.sb13.monew.notification.controller.dto;

import java.time.LocalDateTime;

public record NotificationFindRequest(
        String cursor,
        LocalDateTime after,
        int limit
) {
}

package com.codeit.sb13.monew.notification.service.dto;

import com.codeit.sb13.monew.user.domain.User;

import java.util.UUID;

public record CommentLikedDto(
        User sender,
        User recipient,
        UUID resourceId
) {
}

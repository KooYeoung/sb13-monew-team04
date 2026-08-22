package com.codeit.sb13.monew.comment.repository.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentCommentActivityProjection(
        UUID id,
        UUID articleId,
        String articleTitle,
        UUID userId,
        String userNickname,
        String content,
        Long likeCount,
        LocalDateTime createdAt
) {
}

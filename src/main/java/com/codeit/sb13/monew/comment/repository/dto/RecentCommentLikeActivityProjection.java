package com.codeit.sb13.monew.comment.repository.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentCommentLikeActivityProjection(
        UUID id,
        LocalDateTime createdAt,
        UUID commentId,
        UUID articleId,
        String articleTitle,
        UUID commentUserId,
        String commentUserNickname,
        String commentContent,
        Long commentLikeCount,
        LocalDateTime commentCreatedAt
) {
}

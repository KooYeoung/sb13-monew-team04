package com.codeit.sb13.monew.activity.service.dto;

import com.codeit.sb13.monew.comment.service.dto.RecentCommentLikeActivityDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentCommentLike(
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

    public static RecentCommentLike from(RecentCommentLikeActivityDto recent) {
        return new RecentCommentLike(
                recent.id(),
                recent.createdAt(),
                recent.commentId(),
                recent.articleId(),
                recent.articleTitle(),
                recent.commentUserId(),
                recent.commentUserNickname(),
                recent.commentContent(),
                recent.commentLikeCount(),
                recent.commentCreatedAt()
        );
    }
}

package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.repository.dto.RecentCommentLikeActivityProjection;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentCommentLikeActivityDto(
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

    public static RecentCommentLikeActivityDto from(RecentCommentLikeActivityProjection projection) {
        return new RecentCommentLikeActivityDto(
                projection.id(),
                projection.createdAt(),
                projection.commentId(),
                projection.articleId(),
                projection.articleTitle(),
                projection.commentUserId(),
                projection.commentUserNickname(),
                projection.commentContent(),
                projection.commentLikeCount(),
                projection.commentCreatedAt()
        );
    }
}

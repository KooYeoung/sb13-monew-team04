package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentCommentActivityDto(
        UUID id,
        UUID articleId,
        String articleTitle,
        UUID userId,
        String userNickname,
        String content,
        Long likeCount,
        LocalDateTime createdAt
) {

    public static RecentCommentActivityDto from(RecentCommentActivityProjection projection) {
        return new RecentCommentActivityDto(
                projection.id(),
                projection.articleId(),
                projection.articleTitle(),
                projection.userId(),
                projection.userNickname(),
                projection.content(),
                projection.likeCount(),
                projection.createdAt()
        );
    }
}

package com.codeit.sb13.monew.activity.service.dto;

import com.codeit.sb13.monew.comment.service.dto.RecentCommentActivityDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentComment(
        UUID id,
        UUID articleId,
        String articleTitle,
        UUID userId,
        String userNickname,
        String content,
        Long likeCount,
        LocalDateTime createdAt
) {
    public static RecentComment from(RecentCommentActivityDto recent) {
        return new RecentComment(
                recent.id(),
                recent.articleId(),
                recent.articleTitle(),
                recent.userId(),
                recent.userNickname(),
                recent.content(),
                recent.likeCount(),
                recent.createdAt()
        );
    }
}

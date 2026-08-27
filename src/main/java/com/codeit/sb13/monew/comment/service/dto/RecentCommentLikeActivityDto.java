package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.repository.dto.RecentCommentLikeActivityProjection;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecentCommentLikeActivityDto(
        @Schema(description = "좋아요 ID")
        UUID id,

        @Schema(description = "좋아요한 날짜")
        LocalDateTime createdAt,

        @Schema(description = "댓글 ID")
        UUID commentId,

        @Schema(description = "기사 ID")
        UUID articleId,

        @Schema(description = "기사 제목")
        String articleTitle,

        @Schema(description = "작성자 ID")
        UUID commentUserId,

        @Schema(description = "작성자 닉네임")
        String commentUserNickname,

        @Schema(description = "내용")
        String commentContent,

        @Schema(description = "좋아요 수")
        Long commentLikeCount,

        @Schema(description = "작성된 날짜")
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

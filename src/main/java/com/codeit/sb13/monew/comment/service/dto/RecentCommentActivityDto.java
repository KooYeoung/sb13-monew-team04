package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.repository.dto.RecentCommentActivityProjection;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecentCommentActivityDto(
        @Schema(description = "댓글 ID")
        UUID id,

        @Schema(description = "기사 ID")
        UUID articleId,

        @Schema(description = "기사 제목")
        String articleTitle,

        @Schema(description = "작성자 ID")
        UUID userId,

        @Schema(description = "작성자 닉네임")
        String userNickname,

        @Schema(description = "내용")
        String content,

        @Schema(description = "좋아요 수")
        Long likeCount,

        @Schema(description = "작성된 날짜")
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

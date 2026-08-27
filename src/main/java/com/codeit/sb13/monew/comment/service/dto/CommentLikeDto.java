package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.domain.CommentLike;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public record CommentLikeDto(
    @Schema(description = "좋아요 ID")
    UUID id,

    @Schema(description = "좋아요한 사용자 ID")
    UUID likedBy, // 좋아요한 사용자 ID

    @Schema(description = "좋아요한 날짜")
    LocalDateTime createdAt,

    @Schema(description = "댓글 ID")
    UUID commentId,

    @Schema(description = "기사 ID")
    UUID articleId,

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

  public static CommentLikeDto from(CommentLike commentLike, Long commentLikeCount) {
    return new CommentLikeDto(
        commentLike.getId(),
        commentLike.getLikedBy().getId(),
        commentLike.getCreatedAt(),
        commentLike.getComment().getId(),
        commentLike.getComment().getArticle().getId(),
        commentLike.getComment().getUser().getId(),
        commentLike.getComment().getUser().getNickname(),
        commentLike.getComment().getContent(),
        commentLikeCount,
        commentLike.getComment().getCreatedAt()
    );
  }
}

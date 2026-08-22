package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.domain.CommentLike;
import java.time.LocalDateTime;
import java.util.UUID;

public record CommentLikeDto(
    UUID id,
    UUID likedBy, // 좋아요한 사용자 ID
    LocalDateTime createdAt,
    UUID commentId,
    UUID articleId,
    UUID commentUserId,
    String commentUserNickname,
    String commentContent,
    Long commentLikeCount,
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

package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchProjection;
import java.time.LocalDateTime;
import java.util.UUID;

public record CommentDto(
    UUID id,
    UUID articleId,
    UUID userId,
    String userNickname,
    String content,
    Long likeCount,
    boolean likedByMe,
    LocalDateTime createdAt
) {
  public static CommentDto from(Comment comment, Long likeCount, boolean likedByMe) {
    return new CommentDto(
        comment.getId(),
        comment.getArticle().getId(),
        comment.getUser().getId(),
        comment.getUser().getNickname(), // 사용자 도메인과 연동 후 실제 사용자 이름 조회
        comment.getContent(),
        likeCount,
        likedByMe,
        comment.getCreatedAt()
    );
  }

  public static CommentDto from(CommentSearchProjection projection) {
    return new CommentDto(
        projection.id(),
        projection.articleId(),
        projection.userId(),
        projection.userNickname(),
        projection.content(),
        projection.likeCount(),
        projection.likedByMe(),
        projection.createdAt()
    );
  }
}

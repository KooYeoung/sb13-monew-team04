package com.codeit.sb13.monew.comment.service.dto;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public record CommentDto(
    @Schema(description = "댓글 ID")
    UUID id,
    @Schema(description = "기사 ID")
    UUID articleId,
    @Schema(description = "작성자 ID")
    UUID userId,
    @Schema(description = "작성자 닉네임")
    String userNickname,
    @Schema(description = "내용")
    String content,
    @Schema(description = "좋아요 수")
    Long likeCount,
    @Schema(description = "요청자의 좋아요 여부")
    boolean likedByMe,
    @Schema(description = "작성된 날짜")
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

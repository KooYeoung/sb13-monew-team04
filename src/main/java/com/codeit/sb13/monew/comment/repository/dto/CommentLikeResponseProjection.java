package com.codeit.sb13.monew.comment.repository.dto;

import java.time.LocalDateTime;
import java.util.UUID;


// 엔티티 전체를 다시 로딩하지 않고 응답에 필요한 값과 좋아요 수까지만 조회하기 위한 projection
public record CommentLikeResponseProjection(
    UUID id,
    UUID likedBy,
    LocalDateTime createdAt,
    UUID commentId,
    UUID articleId,
    UUID commentUserId,
    String commentUserNickname,
    String commentContent,
    Long commentLikeCount,
    LocalDateTime commentCreatedAt
) {

}

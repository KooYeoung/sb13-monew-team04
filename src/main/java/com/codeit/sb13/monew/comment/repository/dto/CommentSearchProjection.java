package com.codeit.sb13.monew.comment.repository.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommentSearchProjection(
    UUID id,
    UUID articleId,
    UUID userId,
    String userNickname,
    String content,
    Long likeCount,
    boolean likedByMe,
    LocalDateTime createdAt
) {

}

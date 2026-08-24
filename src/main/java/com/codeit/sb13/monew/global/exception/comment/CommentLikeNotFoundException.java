package com.codeit.sb13.monew.global.exception.comment;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

public class CommentLikeNotFoundException extends CommentException {

  public CommentLikeNotFoundException(UUID commentId, UUID likedById) {
    super(ApiErrorCode.COMMENT_LIKE_NOT_FOUND, Map.of("commentId", commentId, "likedById", likedById));
  }
}

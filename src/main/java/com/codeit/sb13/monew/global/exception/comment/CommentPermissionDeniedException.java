package com.codeit.sb13.monew.global.exception.comment;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;
import java.util.UUID;

public class CommentPermissionDeniedException extends CommentException {

  public CommentPermissionDeniedException(UUID commentId, UUID requestUserId) {
    super(ApiErrorCode.COMMENT_PERMISSION_DENIED,
    Map.of("commentId", commentId, "requestUserId", requestUserId));
  }
}

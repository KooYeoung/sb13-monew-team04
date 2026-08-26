package com.codeit.sb13.monew.global.exception.comment;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

public class InvalidCommentException extends CommentException {

  public InvalidCommentException(String reason) {
    super(ApiErrorCode.COMMENT_INVALID, Map.of("reason", reason));
  }
}

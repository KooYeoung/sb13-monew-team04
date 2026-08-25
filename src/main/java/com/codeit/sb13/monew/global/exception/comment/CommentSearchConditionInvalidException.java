package com.codeit.sb13.monew.global.exception.comment;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import java.util.Map;

public class CommentSearchConditionInvalidException extends CommentException {

  public CommentSearchConditionInvalidException(String reason) {
    super(ApiErrorCode.COMMENT_SEARCH_CONDITION_INVALID, Map.of("reason", reason));
  }
}

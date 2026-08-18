package com.codeit.sb13.monew.global.exception.comment;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class CommentNotFoundException extends CommentException {
    public CommentNotFoundException(UUID commentId) {
        super(ApiErrorCode.COMMENT_NOT_FOUND, Map.of("commentId", commentId));
    }
}

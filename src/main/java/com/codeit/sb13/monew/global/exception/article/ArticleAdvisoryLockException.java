package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class ArticleAdvisoryLockException extends ArticleException {

    public ArticleAdvisoryLockException(String lockKey, String operation) {
        super(ApiErrorCode.ARTICLE_ADVISORY_LOCK_FAILED, Map.of(
                "lockKey", lockKey,
                "operation", operation
        ));
    }

    public ArticleAdvisoryLockException(String lockKey, String operation, Throwable cause) {
        super(ApiErrorCode.ARTICLE_ADVISORY_LOCK_FAILED, Map.of(
                "lockKey", lockKey,
                "operation", operation
        ), cause);
    }
}

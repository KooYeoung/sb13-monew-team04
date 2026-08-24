package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class ArticleBackupFileJsonException extends ArticleException {

    public ArticleBackupFileJsonException(String operation, Throwable cause) {
        super(ApiErrorCode.ARTICLE_BACKUP_FILE_JSON_FAILED, Map.of(
                "operation", operation,
                "cause", cause.getClass().getSimpleName()
        ), cause);
    }
}

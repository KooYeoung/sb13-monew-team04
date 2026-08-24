package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class ArticleBackupFileInvalidException extends ArticleException {

    public ArticleBackupFileInvalidException(String field, String reason) {
        super(ApiErrorCode.ARTICLE_BACKUP_FILE_INVALID, Map.of(
                "field", field,
                "reason", reason
        ));
    }
}

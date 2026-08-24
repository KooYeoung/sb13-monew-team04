package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.time.LocalDate;
import java.util.Map;

public class ArticleBackupFailedException extends ArticleException {

    public ArticleBackupFailedException(LocalDate backupDate, Throwable cause) {
        super(ApiErrorCode.ARTICLE_BACKUP_FAILED, Map.of(
                "backupDate", backupDate,
                "cause", cause.getClass().getSimpleName()
        ), cause);
    }
}

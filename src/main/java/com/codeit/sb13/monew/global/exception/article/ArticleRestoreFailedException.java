package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.time.LocalDate;
import java.util.Map;

public class ArticleRestoreFailedException extends ArticleException {

    public ArticleRestoreFailedException(LocalDate restoreDate, Throwable cause) {
        super(ApiErrorCode.ARTICLE_RESTORE_FAILED, Map.of(
                "restoreDate", restoreDate,
                "cause", cause.getClass().getSimpleName()
        ), cause);
    }
}

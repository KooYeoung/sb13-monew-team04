package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

public class ArticleViewConflictException extends ArticleException {
    public ArticleViewConflictException() {
        super(ApiErrorCode.ARTICLE_VIEW_CONFLICT, null);
    }
}
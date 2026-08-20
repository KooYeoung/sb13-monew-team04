package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

public class ArticleDuplicateException extends ArticleException {
    public ArticleDuplicateException() {
        super(ApiErrorCode.ARTICLE_DUPLICATE, null);
    }
}
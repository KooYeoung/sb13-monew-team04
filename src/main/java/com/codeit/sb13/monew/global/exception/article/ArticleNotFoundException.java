package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

public class ArticleNotFoundException extends ArticleException {

    public ArticleNotFoundException(UUID articleId) {
        super(ApiErrorCode.ARTICLE_NOT_FOUND, Map.of("articleId", articleId));
    }
}

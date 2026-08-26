package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class ArticleSearchConditionInvalidException extends ArticleException {

    public ArticleSearchConditionInvalidException(String reason) {
        super(ApiErrorCode.ARTICLE_SEARCH_CONDITION_INVALID, Map.of("reason", reason));
    }
}
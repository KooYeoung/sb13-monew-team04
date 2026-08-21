package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class ArticleFetchRequestInvalidException extends ArticleException {
    public ArticleFetchRequestInvalidException(String parameter) {
        super(ApiErrorCode.ARTICLE_FETCH_REQUEST_INVALID, Map.of("parameter", parameter));
    }
}

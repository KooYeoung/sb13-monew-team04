package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class ArticleFetchParseException extends ArticleException {
    public ArticleFetchParseException(String source, Throwable cause) {
        super(
                ApiErrorCode.ARTICLE_FETCH_PARSE_FAILED,
                Map.of("source", source, "cause", cause.getClass().getSimpleName())
        );
    }
}

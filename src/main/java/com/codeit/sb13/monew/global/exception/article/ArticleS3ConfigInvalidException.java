package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.util.Map;

public class ArticleS3ConfigInvalidException extends ArticleException {
    public ArticleS3ConfigInvalidException(String property, String reason) {
        super(ApiErrorCode.ARTICLE_S3_CONFIG_INVALID, Map.of(
                "property", property,
                "reason", reason
        ));
    }
}

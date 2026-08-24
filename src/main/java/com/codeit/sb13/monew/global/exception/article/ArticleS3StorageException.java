package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Map;

public class ArticleS3StorageException extends ArticleException {
    public ArticleS3StorageException(String operation, String bucket, String key, S3Exception cause) {
        super(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED, Map.of(
                "operation", operation,
                "bucket", bucket,
                "key", key,
                "statusCode", cause.statusCode()
        ), cause);
    }

    public ArticleS3StorageException(String operation, String bucket, String key, SdkClientException cause) {
        super(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED, Map.of(
                "operation", operation,
                "bucket", bucket,
                "key", key
        ), cause);
    }
}

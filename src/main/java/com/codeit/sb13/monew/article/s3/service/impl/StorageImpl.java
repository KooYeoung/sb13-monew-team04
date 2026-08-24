package com.codeit.sb13.monew.article.s3.service.impl;

import com.codeit.sb13.monew.article.s3.config.S3Properties;
import com.codeit.sb13.monew.article.s3.service.Storage;
import com.codeit.sb13.monew.article.s3.service.dto.StorageCommand;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSaveResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSearchCommand;
import com.codeit.sb13.monew.global.exception.article.ArticleS3ConfigInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleS3StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageImpl implements Storage {

    private final S3Client s3Client;
    private final S3Properties props;

    @Override
    public StorageSaveResult saveIfAbsent(StorageCommand command) {
        String bucket = bucket();
        String key = resolveKey(command.backupDate());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(command.contentType())
                    .ifNoneMatch("*")
                    .build();

            s3Client.putObject(request, RequestBody.fromString(command.content(), StandardCharsets.UTF_8));
            return StorageSaveResult.SAVED;
        } catch (S3Exception e) {
            if (isAlreadyExists(e)) {
                return StorageSaveResult.ALREADY_EXISTS;
            }
            if (isConditionalConflict(e)) {
                return StorageSaveResult.CONFLICT;
            }
            throw new ArticleS3StorageException("putObject", bucket, key, e);
        } catch (SdkClientException e) {
            throw new ArticleS3StorageException("putObject", bucket, key, e);
        }
    }

    @Override
    public Optional<String> find(StorageSearchCommand searchCommand) {
        String bucket = bucket();
        String key = resolveKey(searchCommand.backupDate());

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            ResponseBytes<GetObjectResponse> response = s3Client.getObject(request, ResponseTransformer.toBytes());
            return Optional.of(response.asUtf8String());
        } catch (S3Exception e) {
            if (isNotFound(e)) {
                log.warn("S3 객체를 찾을 수 없습니다. operation=getObject, bucket={}, key={}", bucket, key);
                return Optional.empty();
            }
            throw new ArticleS3StorageException("getObject", bucket, key, e);
        } catch (SdkClientException e) {
            throw new ArticleS3StorageException("getObject", bucket, key, e);
        }
    }

    @Override
    public boolean exists(StorageSearchCommand searchCommand) {
        String bucket = bucket();
        String key = resolveKey(searchCommand.backupDate());

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (S3Exception e) {
            if (isNotFound(e)) {
                log.warn("S3 객체 존재 확인 대상이 없습니다. operation=headObject, bucket={}, key={}", bucket, key);
                return false;
            }
            throw new ArticleS3StorageException("headObject", bucket, key, e);
        } catch (SdkClientException e) {
            throw new ArticleS3StorageException("headObject", bucket, key, e);
        }
    }

    private String bucket() {
        if (!StringUtils.hasText(props.bucket())) {
            throw new ArticleS3ConfigInvalidException("monew.s3.bucket", "S3 bucket 설정이 필요합니다");
        }
        return props.bucket();
    }

    private String resolveKey(LocalDate backupDate) {
        if (backupDate == null) {
            throw new IllegalArgumentException("Storage backupDate must not be null");
        }
        String prefix = prefix();
        return prefix + "/" + backupDate + ".json";
    }

    private String prefix() {
        if (!StringUtils.hasText(props.prefix())) {
            throw new ArticleS3ConfigInvalidException("monew.s3.prefix", "S3 prefix 설정이 필요합니다");
        }
        return props.prefix().trim();
    }

    private boolean isNotFound(S3Exception e) {
        return e.statusCode() == 404;
    }

    private boolean isAlreadyExists(S3Exception e) {
        return e.statusCode() == 412;
    }

    private boolean isConditionalConflict(S3Exception e) {
        return e.statusCode() == 409;
    }
}

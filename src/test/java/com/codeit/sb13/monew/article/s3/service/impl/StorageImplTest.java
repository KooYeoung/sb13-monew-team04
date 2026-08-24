package com.codeit.sb13.monew.article.s3.service.impl;

import com.codeit.sb13.monew.article.s3.config.S3Properties;
import com.codeit.sb13.monew.article.s3.service.dto.StorageCommand;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSaveResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSearchCommand;
import com.codeit.sb13.monew.global.exception.ApiErrorCode;
import com.codeit.sb13.monew.global.exception.article.ArticleS3ConfigInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleS3StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.AccessDeniedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("S3 Storage 단위 테스트")
@ExtendWith(MockitoExtension.class)
class StorageImplTest {

    private static final String BUCKET = "monew-backup";
    private static final LocalDate BACKUP_DATE = LocalDate.of(2026, 8, 23);
    private static final String RESOLVED_KEY = "article-backups/2026-08-23.json";
    private static final String CONTENT = "{\"schemaVersion\":1}";

    @Mock
    private S3Client s3Client;

    private StorageImpl storage;

    @BeforeEach
    void setUp() {
        storage = new StorageImpl(s3Client, s3Properties(BUCKET));
    }

    @Test
    @DisplayName("S3 객체를 기존 객체가 없을 때만 저장한다")
    void savesObjectIfAbsent() throws IOException {
        StorageCommand command = new StorageCommand(BACKUP_DATE, CONTENT, null);

        StorageSaveResult result = storage.saveIfAbsent(command);

        assertThat(result).isEqualTo(StorageSaveResult.SAVED);
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo(RESOLVED_KEY);
        assertThat(request.contentType()).isEqualTo(StorageCommand.DEFAULT_CONTENT_TYPE);
        assertThat(request.ifNoneMatch()).isEqualTo("*");

        byte[] body = bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("contentType이 지정되면 S3 저장 요청에 그대로 사용한다")
    void savesObjectWithExplicitContentType() {
        StorageCommand command = new StorageCommand(BACKUP_DATE, CONTENT, "application/x-ndjson");

        StorageSaveResult result = storage.saveIfAbsent(command);

        assertThat(result).isEqualTo(StorageSaveResult.SAVED);
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/x-ndjson");
    }

    @Test
    @DisplayName("S3 객체가 이미 있으면 ALREADY_EXISTS를 반환한다")
    void returnsAlreadyExistsWhenObjectAlreadyExists() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(s3Exception(412));

        StorageSaveResult result = storage.saveIfAbsent(new StorageCommand(BACKUP_DATE, CONTENT, null));

        assertThat(result).isEqualTo(StorageSaveResult.ALREADY_EXISTS);
    }

    @Test
    @DisplayName("S3 조건부 저장 충돌 시 CONFLICT를 반환한다")
    void returnsConflictWhenConditionalSaveConflicts() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(s3Exception(409));

        StorageSaveResult result = storage.saveIfAbsent(new StorageCommand(BACKUP_DATE, CONTENT, null));

        assertThat(result).isEqualTo(StorageSaveResult.CONFLICT);
    }

    @Test
    @DisplayName("S3 객체 저장 실패 시 커스텀 예외로 원인을 보존한다")
    void wrapsSaveFailureWithCustomException() {
        S3Exception cause = s3FailureException();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> storage.saveIfAbsent(new StorageCommand(BACKUP_DATE, CONTENT, null)))
                .isInstanceOfSatisfying(ArticleS3StorageException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("operation", "putObject")
                            .containsEntry("bucket", BUCKET)
                            .containsEntry("key", RESOLVED_KEY)
                            .containsEntry("statusCode", 500);
                });
    }

    @Test
    @DisplayName("S3 객체 저장 클라이언트 실패 시 커스텀 예외로 원인을 보존한다")
    void wrapsSaveClientFailureWithCustomException() {
        SdkClientException cause = clientFailureException();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> storage.saveIfAbsent(new StorageCommand(BACKUP_DATE, CONTENT, null)))
                .isInstanceOfSatisfying(ArticleS3StorageException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("operation", "putObject")
                            .containsEntry("bucket", BUCKET)
                            .containsEntry("key", RESOLVED_KEY)
                            .doesNotContainKey("statusCode");
                });
    }

    @Test
    @DisplayName("S3 객체를 UTF-8 문자열로 조회한다")
    void findsObject() {
        ResponseBytes<GetObjectResponse> response = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                CONTENT.getBytes(StandardCharsets.UTF_8)
        );
        when(s3Client.getObject(
                any(GetObjectRequest.class),
                anyResponseTransformer()
        )).thenReturn(response);

        Optional<String> result = storage.find(new StorageSearchCommand(BACKUP_DATE));

        assertThat(result).contains(CONTENT);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture(), anyResponseTransformer());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(RESOLVED_KEY);
    }

    @Test
    @DisplayName("S3 객체가 없으면 빈 Optional을 반환한다")
    void returnsEmptyWhenObjectDoesNotExist() {
        when(s3Client.getObject(
                any(GetObjectRequest.class),
                anyResponseTransformer()
        )).thenThrow(notFoundException());

        Optional<String> result = storage.find(new StorageSearchCommand(BACKUP_DATE));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("S3 객체 조회 실패 시 커스텀 예외로 원인을 보존한다")
    void wrapsFindFailureWithCustomException() {
        S3Exception cause = s3FailureException();
        when(s3Client.getObject(
                any(GetObjectRequest.class),
                anyResponseTransformer()
        )).thenThrow(cause);

        assertThatThrownBy(() -> storage.find(new StorageSearchCommand(BACKUP_DATE)))
                .isInstanceOfSatisfying(ArticleS3StorageException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("operation", "getObject")
                            .containsEntry("bucket", BUCKET)
                            .containsEntry("key", RESOLVED_KEY)
                            .containsEntry("statusCode", 500);
                });
    }

    @Test
    @DisplayName("S3 객체 조회 클라이언트 실패 시 커스텀 예외로 원인을 보존한다")
    void wrapsFindClientFailureWithCustomException() {
        SdkClientException cause = clientFailureException();
        when(s3Client.getObject(
                any(GetObjectRequest.class),
                anyResponseTransformer()
        )).thenThrow(cause);

        assertThatThrownBy(() -> storage.find(new StorageSearchCommand(BACKUP_DATE)))
                .isInstanceOfSatisfying(ArticleS3StorageException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("operation", "getObject")
                            .containsEntry("bucket", BUCKET)
                            .containsEntry("key", RESOLVED_KEY)
                            .doesNotContainKey("statusCode");
                });
    }

    @Test
    @DisplayName("S3 객체 존재 여부를 확인한다")
    void checksObjectExists() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        boolean result = storage.exists(new StorageSearchCommand(BACKUP_DATE));

        assertThat(result).isTrue();

        ArgumentCaptor<HeadObjectRequest> requestCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(RESOLVED_KEY);
    }

    @Test
    @DisplayName("S3 객체가 없으면 존재 여부를 false로 반환한다")
    void returnsFalseWhenObjectDoesNotExist() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(notFoundException());

        boolean result = storage.exists(new StorageSearchCommand(BACKUP_DATE));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("S3 객체 존재 확인 실패 시 커스텀 예외로 원인을 보존한다")
    void wrapsExistsFailureWithCustomException() {
        S3Exception cause = s3FailureException();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> storage.exists(new StorageSearchCommand(BACKUP_DATE)))
                .isInstanceOfSatisfying(ArticleS3StorageException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("operation", "headObject")
                            .containsEntry("bucket", BUCKET)
                            .containsEntry("key", RESOLVED_KEY)
                            .containsEntry("statusCode", 500);
                });
    }

    @Test
    @DisplayName("S3 객체 존재 확인 클라이언트 실패 시 커스텀 예외로 원인을 보존한다")
    void wrapsExistsClientFailureWithCustomException() {
        SdkClientException cause = clientFailureException();
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> storage.exists(new StorageSearchCommand(BACKUP_DATE)))
                .isInstanceOfSatisfying(ArticleS3StorageException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_STORAGE_FAILED);
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("operation", "headObject")
                            .containsEntry("bucket", BUCKET)
                            .containsEntry("key", RESOLVED_KEY)
                            .doesNotContainKey("statusCode");
                });
    }

    @Test
    @DisplayName("bucket 설정이 비어 있으면 실패한다")
    void failsWhenBucketIsBlank() {
        StorageImpl blankBucketStorage = new StorageImpl(s3Client, s3Properties(""));

        assertThatThrownBy(() -> blankBucketStorage.saveIfAbsent(new StorageCommand(BACKUP_DATE, CONTENT, null)))
                .isInstanceOfSatisfying(ArticleS3ConfigInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_CONFIG_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("property", "monew.s3.bucket")
                            .containsEntry("reason", "S3 bucket 설정이 필요합니다");
                });
    }

    @Test
    @DisplayName("prefix 설정이 비어 있으면 실패한다")
    void failsWhenPrefixIsBlank() {
        StorageImpl blankPrefixStorage = new StorageImpl(s3Client, s3Properties(BUCKET, " "));

        assertThatThrownBy(() -> blankPrefixStorage.saveIfAbsent(new StorageCommand(BACKUP_DATE, CONTENT, null)))
                .isInstanceOfSatisfying(ArticleS3ConfigInvalidException.class, e -> {
                    assertThat(e.getApiErrorCode()).isEqualTo(ApiErrorCode.ARTICLE_S3_CONFIG_INVALID);
                    assertThat(e.getDetails())
                            .containsEntry("property", "monew.s3.prefix")
                            .containsEntry("reason", "S3 prefix 설정이 필요합니다");
                });
    }

    @Test
    @DisplayName("StorageCommand 필수 값이 비어 있으면 실패한다")
    void failsWhenStorageCommandRequiredValueIsBlank() {
        assertThatThrownBy(() -> new StorageCommand(null, CONTENT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backupDate");
        assertThatThrownBy(() -> new StorageCommand(BACKUP_DATE, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    @DisplayName("StorageSearchCommand backupDate가 비어 있으면 실패한다")
    void failsWhenStorageSearchCommandBackupDateIsNull() {
        assertThatThrownBy(() -> new StorageSearchCommand(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backupDate");
    }

    private S3Properties s3Properties(String bucket) {
        return s3Properties(bucket, "article-backups");
    }

    private S3Properties s3Properties(String bucket, String prefix) {
        return new S3Properties(bucket, "us-east-1", "", prefix, true, "0 10 0 * * *");
    }

    private S3Exception notFoundException() {
        return NoSuchKeyException.builder()
                .statusCode(404)
                .message("not found")
                .build();
    }

    private S3Exception s3FailureException() {
        return AccessDeniedException.builder()
                .statusCode(500)
                .message("s3 failure")
                .build();
    }

    private S3Exception s3Exception(int statusCode) {
        return AccessDeniedException.builder()
                .statusCode(statusCode)
                .message("s3 failure")
                .build();
    }

    private SdkClientException clientFailureException() {
        return SdkClientException.builder()
                .message("client failure")
                .build();
    }

    @SuppressWarnings("unchecked")
    private ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>> anyResponseTransformer() {
        return any(ResponseTransformer.class);
    }
}

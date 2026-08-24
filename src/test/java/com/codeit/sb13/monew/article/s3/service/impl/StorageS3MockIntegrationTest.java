package com.codeit.sb13.monew.article.s3.service.impl;

import com.codeit.sb13.monew.article.s3.config.S3Properties;
import com.codeit.sb13.monew.article.s3.service.dto.StorageCommand;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSaveResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSearchCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("S3 Storage S3Mock 통합 테스트")
class StorageS3MockIntegrationTest {

    private static final String BUCKET = "monew-backup";
    private static final DockerImageName S3_MOCK_IMAGE = DockerImageName.parse("adobe/s3mock:4.11.0");
    private static GenericContainer<?> s3Mock;

    private S3Client s3Client;
    private StorageImpl storage;

    @BeforeAll
    static void startS3Mock() {
        if (!isDockerAvailable()) {
            throw new TestAbortedException("Docker daemon is not available; skipping S3Mock integration test");
        }

        s3Mock = new GenericContainer<>(S3_MOCK_IMAGE)
                .withEnv("initialBuckets", BUCKET)
                .withExposedPorts(9090);

        s3Mock.start();
    }

    @AfterAll
    static void stopS3Mock() {
        if (s3Mock != null) {
            s3Mock.stop();
        }
    }

    @BeforeEach
    void setUp() {
        String endpoint = "http://" + s3Mock.getHost() + ":" + s3Mock.getMappedPort(9090);
        s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .build();
        createBucketIfMissing();
        storage = new StorageImpl(
                s3Client,
                new S3Properties(BUCKET, "us-east-1", endpoint, "article-backups", true, "0 10 0 * * *")
        );
    }

    @Test
    @DisplayName("S3Mock에 객체를 저장하고 조회한다")
    void savesAndFindsObjectUsingS3Mock() {
        LocalDate backupDate = LocalDate.of(2026, 8, 23);
        String content = "{\"schemaVersion\":1,\"articleCount\":0}";

        StorageSaveResult result = storage.saveIfAbsent(new StorageCommand(backupDate, content, null));

        assertThat(result).isEqualTo(StorageSaveResult.SAVED);
        assertThat(storage.exists(new StorageSearchCommand(backupDate))).isTrue();
        assertThat(storage.find(new StorageSearchCommand(backupDate))).contains(content);
    }

    @Test
    @DisplayName("S3Mock에 없는 객체는 없음으로 처리한다")
    void handlesMissingObjectUsingS3Mock() {
        LocalDate backupDate = LocalDate.of(2026, 8, 24);

        assertThat(storage.exists(new StorageSearchCommand(backupDate))).isFalse();
        assertThat(storage.find(new StorageSearchCommand(backupDate))).isEmpty();
    }

    private void createBucketIfMissing() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 409) {
                throw e;
            }
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | Error e) {
            return false;
        }
    }
}

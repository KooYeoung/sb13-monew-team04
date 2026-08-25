package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreCommand;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSaveResult;
import com.codeit.sb13.monew.article.s3.service.dto.StorageSearchCommand;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "monew.backup.schedule.enabled=false",
        "spring.flyway.out-of-order=true"
})
@ActiveProfiles("dev")
@Tag("external")
@Tag("s3-dev-external")
@DisplayName("dev 설정 기반 실제 S3 기사 백업/복구 smoke 테스트")
class ArticleS3DevExternalSmokeTest {

    private static final String RUN_ID = runId();
    private static final String TEST_PREFIX = configuredPrefix() + "/external-smoke/" + RUN_ID;
    private static final String LINK = "https://monew.example.com/s3-dev-external/" + RUN_ID;
    private static final String TITLE = "S3 dev 백업 복구 테스트 기사 " + RUN_ID;
    private static final String SUMMARY = "실제 dev 설정으로 S3에 백업한 뒤 같은 백업 파일로 복구되는지 확인합니다.";

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleBackupService articleBackupService;

    @Autowired
    private ArticleRestoreService articleRestoreService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private Storage storage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void s3DevExternalProperties(DynamicPropertyRegistry registry) {
        registry.add("monew.s3.prefix", () -> TEST_PREFIX);
    }

    @Test
    @DisplayName("백업 서비스가 실제 S3에 저장한 백업 파일로 기사 row를 복구한다")
    void backsUpArticleToConfiguredS3AndRestoresMissingArticleFromThatBackup() {
        LocalDate backupDate = LocalDate.now().plusYears(20);
        LocalDateTime publishedAt = backupDate.atTime(10, 15, 30);
        ArticleRequest request = new ArticleRequest(
                TITLE,
                SUMMARY,
                LINK,
                publishedAt,
                ArticleSource.NAVER
        );

        deleteArticleByLink(LINK);

        try {
            Article originalArticle = articleService.create(request);

            StorageSaveResult backupResult = articleBackupService.backupArticlesByDate(backupDate);
            assertThat(backupResult).isEqualTo(StorageSaveResult.SAVED);

            StorageSearchCommand searchCommand = new StorageSearchCommand(backupDate);
            assertThat(storage.exists(searchCommand)).isTrue();
            assertThat(storage.find(searchCommand))
                    .hasValueSatisfying(json -> assertThat(json)
                            .contains(LINK)
                            .contains(TITLE)
                            .contains(SUMMARY));

            deleteArticleByLink(LINK);
            assertThat(articleRepository.findByLink(LINK)).isEmpty();

            List<ArticleRestoreResult> restoreResults = articleRestoreService.restoreArticles(
                    new ArticleRestoreCommand(backupDate, backupDate)
            );

            assertThat(restoreResults).hasSize(1);
            ArticleRestoreResult restoreResult = restoreResults.get(0);
            assertThat(restoreResult.restoreDate()).isEqualTo(backupDate);
            assertThat(restoreResult.restoredArticleCount()).isEqualTo(1L);
            assertThat(restoreResult.restoredArticleIds()).hasSize(1);

            Article restoredArticle = articleRepository.findByLink(LINK).orElseThrow();
            assertThat(restoredArticle.getId()).isEqualTo(restoreResult.restoredArticleIds().get(0));
            assertThat(restoredArticle.getId()).isNotEqualTo(originalArticle.getId());
            assertThat(restoredArticle.getTitle()).isEqualTo(TITLE);
            assertThat(restoredArticle.getSummary()).isEqualTo(SUMMARY);
            assertThat(restoredArticle.getSource()).isEqualTo(ArticleSource.NAVER);
            assertThat(restoredArticle.getDate()).isEqualTo(publishedAt);

            List<ArticleRestoreResult> duplicateRestoreResults = articleRestoreService.restoreArticles(
                    new ArticleRestoreCommand(backupDate, backupDate)
            );
            assertThat(duplicateRestoreResults).hasSize(1);
            assertThat(duplicateRestoreResults.get(0).restoredArticleCount()).isZero();
        } finally {
            deleteArticleByLink(LINK);
        }
    }

    private void deleteArticleByLink(String link) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            articleRepository.findByLink(link).ifPresent(articleRepository::delete);
            articleRepository.flush();
        });
    }

    private static String configuredPrefix() {
        String prefix = configuredValue("MONEW_ARTICLE_BACKUP_S3_PREFIX");
        if (!StringUtils.hasText(prefix)) {
            throw new IllegalStateException("MONEW_ARTICLE_BACKUP_S3_PREFIX 설정이 필요합니다.");
        }
        return trimSlashes(prefix);
    }

    private static String configuredValue(String key) {
        String value = System.getenv(key);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return loadEnvDev().get(key);
    }

    private static Map<String, String> loadEnvDev() {
        Path envDevPath = Path.of(".env.dev");
        if (!Files.isRegularFile(envDevPath)) {
            return Map.of();
        }

        try {
            Map<String, String> values = new HashMap<>();
            Files.readAllLines(envDevPath).forEach(line -> putEnvValue(values, line));
            return values;
        } catch (IOException e) {
            throw new IllegalStateException(".env.dev 파일을 읽지 못했습니다. path=" + envDevPath, e);
        }
    }

    private static void putEnvValue(Map<String, String> values, String line) {
        String normalizedLine = line.strip();
        if (!StringUtils.hasText(normalizedLine) || normalizedLine.startsWith("#")) {
            return;
        }
        if (normalizedLine.startsWith("export ")) {
            normalizedLine = normalizedLine.substring("export ".length()).strip();
        }

        int separatorIndex = normalizedLine.indexOf('=');
        if (separatorIndex < 0) {
            return;
        }

        String key = normalizedLine.substring(0, separatorIndex).strip();
        String value = stripQuotes(normalizedLine.substring(separatorIndex + 1).strip());
        if (StringUtils.hasText(key)) {
            values.put(key, value);
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String trimSlashes(String value) {
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private static String runId() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + suffix;
    }
}

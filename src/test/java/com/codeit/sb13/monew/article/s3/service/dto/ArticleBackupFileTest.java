package com.codeit.sb13.monew.article.s3.service.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ArticleBackupFile 단위 테스트")
class ArticleBackupFileTest {

    @Test
    @DisplayName("백업 파일 모델이 필수 필드와 기사 수를 가진다")
    void createsArticleBackupFileWithRequiredFieldsAndArticleCount() {
        // given
        ArticleBackupItem item = backupItem();
        LocalDate backupDate = LocalDate.of(2026, 8, 23);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 24, 0, 10);

        // when
        ArticleBackupFile backupFile = ArticleBackupFile.of(backupDate, generatedAt, List.of(item));

        // then
        assertThat(backupFile.schemaVersion()).isEqualTo(ArticleBackupFile.CURRENT_SCHEMA_VERSION);
        assertThat(backupFile.backupDate()).isEqualTo(backupDate);
        assertThat(backupFile.generatedAt()).isEqualTo(generatedAt);
        assertThat(backupFile.articleCount()).isEqualTo(1L);
        assertThat(backupFile.articles()).containsExactly(item);
    }

    @Test
    @DisplayName("스키마 버전이 1이 아니면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenSchemaVersionIsNotCurrentVersion() {
        assertThatThrownBy(() -> new ArticleBackupFile(
                2,
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                1L,
                List.of(backupItem())
        )).isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    @Test
    @DisplayName("기사 수와 기사 목록 크기가 다르면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenArticleCountDoesNotMatchArticlesSize() {
        assertThatThrownBy(() -> new ArticleBackupFile(
                ArticleBackupFile.CURRENT_SCHEMA_VERSION,
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                2L,
                List.of(backupItem())
        )).isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    @Test
    @DisplayName("백업 파일 필수 값이 없으면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenRequiredValuesAreMissing() {
        assertThatThrownBy(() -> new ArticleBackupFile(
                null,
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                1L,
                List.of(backupItem())
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupFile(
                ArticleBackupFile.CURRENT_SCHEMA_VERSION,
                null,
                LocalDateTime.of(2026, 8, 24, 0, 10),
                1L,
                List.of(backupItem())
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupFile(
                ArticleBackupFile.CURRENT_SCHEMA_VERSION,
                LocalDate.of(2026, 8, 23),
                null,
                1L,
                List.of(backupItem())
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupFile(
                ArticleBackupFile.CURRENT_SCHEMA_VERSION,
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                1L,
                null
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupFile(
                ArticleBackupFile.CURRENT_SCHEMA_VERSION,
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                1L,
                Collections.singletonList(null)
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupFile(
                ArticleBackupFile.CURRENT_SCHEMA_VERSION,
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                null,
                List.of(backupItem())
        )).isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    @Test
    @DisplayName("기사 목록이 없으면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenArticlesIsNull() {
        assertThatThrownBy(() -> ArticleBackupFile.of(
                LocalDate.of(2026, 8, 23),
                LocalDateTime.of(2026, 8, 24, 0, 10),
                null
        )).isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    private ArticleBackupItem backupItem() {
        return new ArticleBackupItem(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                "기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        );
    }
}

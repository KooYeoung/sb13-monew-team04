package com.codeit.sb13.monew.article.s3.service.dto;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ArticleBackupItem 단위 테스트")
class ArticleBackupItemTest {

    @Test
    @DisplayName("백업 아이템 모델이 필수 필드를 가진다")
    void createsArticleBackupItemWithRequiredFields() {
        // given
        UUID originalArticleId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 23, 10, 15);

        // when
        ArticleBackupItem item = new ArticleBackupItem(
                originalArticleId,
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                "기사 요약",
                publishedAt,
                null
        );

        // then
        assertThat(item.originalArticleId()).isEqualTo(originalArticleId);
        assertThat(item.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(item.link()).isEqualTo("https://example.com/news/1");
        assertThat(item.title()).isEqualTo("기사 제목");
        assertThat(item.summary()).isEqualTo("기사 요약");
        assertThat(item.publishedAt()).isEqualTo(publishedAt);
        assertThat(item.deletedAt()).isNull();
    }

    @Test
    @DisplayName("summary는 빈 문자열 또는 공백 문자열이어도 백업 아이템을 생성한다")
    void createsArticleBackupItemWithEmptyOrBlankSummary() {
        // given
        UUID originalArticleId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 23, 10, 15);

        // when
        ArticleBackupItem emptySummaryItem = new ArticleBackupItem(
                originalArticleId,
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                "",
                publishedAt,
                null
        );
        ArticleBackupItem blankSummaryItem = new ArticleBackupItem(
                originalArticleId,
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                " ",
                publishedAt,
                null
        );

        // then
        assertThat(emptySummaryItem.summary()).isEmpty();
        assertThat(blankSummaryItem.summary()).isEqualTo(" ");
    }

    @Test
    @DisplayName("Article.date를 publishedAt으로 매핑한다")
    void mapsArticleDateToPublishedAt() {
        // given
        UUID articleId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        LocalDateTime articleDate = LocalDateTime.of(2026, 8, 23, 10, 15);
        Article article = Article.create(
                "기사 제목",
                "기사 요약",
                "https://example.com/news/1",
                articleDate,
                ArticleSource.NAVER
        );
        ReflectionTestUtils.setField(article, "id", articleId);

        // when
        ArticleBackupItem item = ArticleBackupItem.from(article);

        // then
        assertThat(item.publishedAt()).isEqualTo(articleDate);
    }

    @Test
    @DisplayName("originalArticleId를 원본 Article ID로 분리해 보관한다")
    void keepsOriginalArticleIdSeparatedFromRestoredArticleId() {
        // given
        UUID originalArticleId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Article article = Article.create(
                "기사 제목",
                "기사 요약",
                "https://example.com/news/1",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                ArticleSource.NAVER
        );
        ReflectionTestUtils.setField(article, "id", originalArticleId);

        // when
        ArticleBackupItem item = ArticleBackupItem.from(article);

        // then
        assertThat(item.originalArticleId()).isEqualTo(originalArticleId);
    }

    @Test
    @DisplayName("모델 필수값이 없으면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenRequiredValueIsMissing() {
        assertThatThrownBy(() -> new ArticleBackupItem(
                null,
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                "기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupItem(
                UUID.randomUUID(),
                ArticleSource.NAVER,
                " ",
                "기사 제목",
                "기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        )).isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    @Test
    @DisplayName("백업 아이템 필수 값이 없으면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenRemainingRequiredValuesAreMissing() {
        assertThatThrownBy(() -> new ArticleBackupItem(
                UUID.randomUUID(),
                null,
                "https://example.com/news/1",
                "기사 제목",
                "기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupItem(
                UUID.randomUUID(),
                ArticleSource.NAVER,
                "https://example.com/news/1",
                " ",
                "기사 요약",
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        )).isInstanceOf(ArticleBackupFileInvalidException.class);

        assertThatThrownBy(() -> new ArticleBackupItem(
                UUID.randomUUID(),
                ArticleSource.NAVER,
                "https://example.com/news/1",
                "기사 제목",
                null,
                LocalDateTime.of(2026, 8, 23, 10, 15),
                null
        )).isInstanceOf(ArticleBackupFileInvalidException.class);
    }

    @Test
    @DisplayName("백업할 기사가 없으면 백업 파일 검증 예외가 발생한다")
    void throwsInvalidExceptionWhenArticleIsNull() {
        assertThatThrownBy(() -> ArticleBackupItem.from(null))
                .isInstanceOf(ArticleBackupFileInvalidException.class);
    }
}

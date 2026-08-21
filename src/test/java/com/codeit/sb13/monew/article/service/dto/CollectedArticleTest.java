package com.codeit.sb13.monew.article.service.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CollectedArticle 단위 테스트")
class CollectedArticleTest {

    @Test
    @DisplayName("수집 기사 후보 필드를 보존한다")
    void preservesCollectedArticleFields() {
        // given
        ArticleSource source = ArticleSource.NAVER;
        String title = "title";
        String summary = "summary";
        String link = "https://example.com/news";
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 20, 10, 0);

        // when
        CollectedArticle article = new CollectedArticle(source, title, summary, link, publishedAt);

        // then
        assertThat(article.source()).isEqualTo(source);
        assertThat(article.title()).isEqualTo(title);
        assertThat(article.summary()).isEqualTo(summary);
        assertThat(article.link()).isEqualTo(link);
        assertThat(article.publishedAt()).isEqualTo(publishedAt);
    }
}

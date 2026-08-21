package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NewsSourceAdapter 계약 테스트")
class NewsSourceAdapterTest {

    @Test
    @DisplayName("어댑터는 출처가 일치하는 기사 후보를 반환한다")
    void adapterReturnsMatchingSourceArticles() {
        // given
        FakeNewsSourceAdapter adapter = new FakeNewsSourceAdapter();

        // when
        List<CollectedArticle> articles = adapter.fetch();

        // then
        assertThat(adapter.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(articles)
                .hasSize(2)
                .allSatisfy(article -> {
                    assertThat(article.source()).isEqualTo(adapter.source());
                })
                .first()
                .satisfies(article -> {
                    assertThat(article.title()).isEqualTo("title 1");
                    assertThat(article.summary()).isEqualTo("summary");
                    assertThat(article.link()).isEqualTo("https://example.com/news/1");
                    assertThat(article.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 10, 0));
                });
    }

    private static class FakeNewsSourceAdapter implements NewsSourceAdapter {
        @Override
        public ArticleSource source() {
            return ArticleSource.NAVER;
        }

        @Override
        public List<CollectedArticle> fetch() {
            return List.of(
                    article("title 1", "https://example.com/news/1"),
                    article("title 2", "https://example.com/news/2")
            );
        }

        private CollectedArticle article(String title, String link) {
            return new CollectedArticle(
                    source(),
                    title,
                    "summary",
                    link,
                    LocalDateTime.of(2026, 8, 20, 10, 0)
            );
        }
    }
}

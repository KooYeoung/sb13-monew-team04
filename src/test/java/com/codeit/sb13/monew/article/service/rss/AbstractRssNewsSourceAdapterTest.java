package com.codeit.sb13.monew.article.service.rss;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.rss.category.ChosunRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.HankyungRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.YonhapRssCategory;
import com.codeit.sb13.monew.article.service.rss.client.RssNewsClient;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("RSS 뉴스 소스 어댑터 단위 테스트")
class AbstractRssNewsSourceAdapterTest {

    @Test
    @DisplayName("출처별 source를 반환한다")
    void returnsSourceByAdapter() {
        RssNewsClient client = mock(RssNewsClient.class);

        assertThat(new HankyungRssNewsSourceAdapter(client).source()).isEqualTo(ArticleSource.HANKYUNG);
        assertThat(new ChosunRssNewsSourceAdapter(client).source()).isEqualTo(ArticleSource.CHOSUN);
        assertThat(new YonhapRssNewsSourceAdapter(client).source()).isEqualTo(ArticleSource.YEONHAP);
    }

    @Test
    @DisplayName("기본 fetch는 출처별 대표 카테고리 1개만 호출한다")
    void fetchesDefaultCategoryOnly() {
        RssNewsClient client = mock(RssNewsClient.class);
        CollectedArticle hankyungArticle = article(ArticleSource.HANKYUNG, "hankyung");
        CollectedArticle chosunArticle = article(ArticleSource.CHOSUN, "chosun");
        CollectedArticle yonhapArticle = article(ArticleSource.YEONHAP, "yonhap");
        when(client.fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ALL_NEWS)).thenReturn(List.of(hankyungArticle));
        when(client.fetch(ArticleSource.CHOSUN, ChosunRssCategory.ALL)).thenReturn(List.of(chosunArticle));
        when(client.fetch(ArticleSource.YEONHAP, YonhapRssCategory.LATEST)).thenReturn(List.of(yonhapArticle));

        assertThat(new HankyungRssNewsSourceAdapter(client).fetch()).containsExactly(hankyungArticle);
        assertThat(new ChosunRssNewsSourceAdapter(client).fetch()).containsExactly(chosunArticle);
        assertThat(new YonhapRssNewsSourceAdapter(client).fetch()).containsExactly(yonhapArticle);

        verify(client).fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ALL_NEWS);
        verify(client).fetch(ArticleSource.CHOSUN, ChosunRssCategory.ALL);
        verify(client).fetch(ArticleSource.YEONHAP, YonhapRssCategory.LATEST);
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("fetch는 전달받은 카테고리만 호출하고 결과를 합산한다")
    void fetchesRequestedCategoriesOnly() {
        RssNewsClient client = mock(RssNewsClient.class);
        HankyungRssNewsSourceAdapter adapter = new HankyungRssNewsSourceAdapter(client);
        CollectedArticle economyArticle = article(ArticleSource.HANKYUNG, "economy");
        CollectedArticle financeArticle = article(ArticleSource.HANKYUNG, "finance");
        when(client.fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ECONOMY)).thenReturn(List.of(economyArticle));
        when(client.fetch(ArticleSource.HANKYUNG, HankyungRssCategory.FINANCE)).thenReturn(List.of(financeArticle));

        List<CollectedArticle> articles = adapter.fetch(List.of("economy", "finance"));

        assertThat(articles).containsExactly(economyArticle, financeArticle);
        verify(client).fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ECONOMY);
        verify(client).fetch(ArticleSource.HANKYUNG, HankyungRssCategory.FINANCE);
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("카테고리 목록이 비어 있으면 client 호출 없이 빈 목록을 반환한다")
    void returnsEmptyListWhenCategoryKeysAreEmpty() {
        RssNewsClient client = mock(RssNewsClient.class);
        HankyungRssNewsSourceAdapter adapter = new HankyungRssNewsSourceAdapter(client);

        assertThat(adapter.fetch(null)).isEmpty();
        assertThat(adapter.fetch(List.of())).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("잘못된 category key면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenCategoryKeyIsInvalid() {
        RssNewsClient client = mock(RssNewsClient.class);
        HankyungRssNewsSourceAdapter adapter = new HankyungRssNewsSourceAdapter(client);

        assertThatThrownBy(() -> adapter.fetch(List.of("unknown")))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("카테고리 key 전체 검증이 실패하면 client를 호출하지 않는다")
    void doesNotFetchWhenAnyCategoryKeyIsInvalid() {
        RssNewsClient client = mock(RssNewsClient.class);
        HankyungRssNewsSourceAdapter adapter = new HankyungRssNewsSourceAdapter(client);

        assertThatThrownBy(() -> adapter.fetch(List.of("economy", "unknown")))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        verifyNoInteractions(client);
    }

    private CollectedArticle article(ArticleSource source, String title) {
        return new CollectedArticle(
                source,
                title,
                "summary",
                "https://example.com/articles/" + title,
                LocalDateTime.of(2026, 8, 21, 10, 0)
        );
    }
}

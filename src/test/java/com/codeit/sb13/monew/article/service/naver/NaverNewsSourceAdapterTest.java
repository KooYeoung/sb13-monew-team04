package com.codeit.sb13.monew.article.service.naver;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.client.NaverNewsClient;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import com.codeit.sb13.monew.article.service.naver.provider.NaverNewsSearchRequestProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("네이버 뉴스 소스 어댑터 단위 테스트")
class NaverNewsSourceAdapterTest {

    @Mock
    private NaverNewsSearchRequestProvider provider;

    @Mock
    private NaverNewsClient client;

    private NaverNewsSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NaverNewsSourceAdapter(provider, client);
    }

    @Test
    @DisplayName("출처가 NAVER로 반환된다")
    void sourceReturnsNaver() {
        // when
        ArticleSource source = adapter.source();

        // then
        assertThat(source).isEqualTo(ArticleSource.NAVER);
    }

    @Test
    @DisplayName("요청 목록이 비어 있으면 클라이언트 호출 없이 빈 목록을 반환한다")
    void emptyProviderRequestsReturnEmptyListWithoutClientCall() {
        // given
        when(provider.getRequests()).thenReturn(List.of());

        // when
        List<CollectedArticle> articles = adapter.fetch();

        // then
        assertThat(articles).isEmpty();
        verify(provider).getRequests();
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("요청 순서대로 수집 결과를 합산한다")
    void fetchCombinesClientResultsInRequestOrder() {
        // given
        NaverNewsSearchRequest economyRequest = new NaverNewsSearchRequest("economy");
        NaverNewsSearchRequest sportsRequest = new NaverNewsSearchRequest("sports");

        CollectedArticle economyArticle1 = article("economy title 1", "https://example.com/economy/1");
        CollectedArticle economyArticle2 = article("economy title 2", "https://example.com/economy/2");
        CollectedArticle sportsArticle = article("sports title", "https://example.com/sports/1");

        when(provider.getRequests()).thenReturn(List.of(economyRequest, sportsRequest));
        when(client.search(economyRequest)).thenReturn(List.of(economyArticle1, economyArticle2));
        when(client.search(sportsRequest)).thenReturn(List.of(sportsArticle));

        // when
        List<CollectedArticle> articles = adapter.fetch();

        // then
        assertThat(articles).containsExactly(economyArticle1, economyArticle2, sportsArticle);

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).search(economyRequest);
        inOrder.verify(client).search(sportsRequest);
    }

    private CollectedArticle article(String title, String link) {
        return new CollectedArticle(
                ArticleSource.NAVER,
                title,
                "summary",
                link,
                LocalDateTime.of(2026, 8, 21, 10, 0)
        );
    }
}

package com.codeit.sb13.monew.article.service.naver.mapper;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsItem;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchResponse;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NaverNewsMapper 단위 테스트")
class NaverNewsMapperTest {

    private final NaverNewsMapper mapper = new NaverNewsMapper();

    @Test
    @DisplayName("NAVER 응답을 수집 기사 후보로 변환한다")
    void convertsNaverResponseToCollectedArticles() {
        // given
        NaverNewsSearchResponse response = responseOf(new NaverNewsItem(
                "<b>경제</b> &quot;뉴스&quot;",
                "https://example.com/original?query=news&amp;page=1",
                "https://n.news.naver.com/article/001/0000000001",
                "국내 &lt;b&gt;증시&lt;/b&gt; &amp; 환율",
                "Thu, 20 Aug 2026 10:15:30 +0900"
        ));

        // when
        List<CollectedArticle> articles = mapper.toCollectedArticles(response);

        // then
        assertThat(articles).hasSize(1);
        CollectedArticle article = articles.get(0);
        assertThat(article.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(article.title()).isEqualTo("경제 \"뉴스\"");
        assertThat(article.summary()).isEqualTo("국내 증시 & 환율");
        assertThat(article.link()).isEqualTo("https://example.com/original?query=news&page=1");
        assertThat(article.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 10, 15, 30));
    }

    @Test
    @DisplayName("originallink가 비어 있으면 link를 사용한다")
    void usesLinkWhenOriginalLinkIsBlank() {
        // given
        NaverNewsSearchResponse response = responseOf(new NaverNewsItem(
                "제목",
                "   ",
                "https://n.news.naver.com/article/001/0000000001?query=news&amp;page=1",
                "요약",
                "Thu, 20 Aug 2026 10:15:30 +0900"
        ));

        // when
        List<CollectedArticle> articles = mapper.toCollectedArticles(response);

        // then
        assertThat(articles)
                .singleElement()
                .satisfies(article -> assertThat(article.link())
                        .isEqualTo("https://n.news.naver.com/article/001/0000000001?query=news&page=1"));
    }

    @Test
    @DisplayName("pubDate 파싱에 실패하면 예외 발생")
    void throwsExceptionWhenPubDateIsInvalid() {
        // given
        NaverNewsSearchResponse response = responseOf(new NaverNewsItem(
                "제목",
                "https://example.com/original",
                "https://n.news.naver.com/article/001/0000000001",
                "요약",
                "invalid-date"
        ));

        // when & then
        assertThatThrownBy(() -> mapper.toCollectedArticles(response))
                .isInstanceOf(ArticleFetchParseException.class);
    }

    private NaverNewsSearchResponse responseOf(NaverNewsItem item) {
        return new NaverNewsSearchResponse(
                "Thu, 20 Aug 2026 10:16:00 +0900",
                1,
                1,
                1,
                List.of(item)
        );
    }
}

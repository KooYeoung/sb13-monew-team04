package com.codeit.sb13.monew.article.service.rss;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.rss.category.ChosunRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.HankyungRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import com.codeit.sb13.monew.article.service.rss.category.YonhapRssCategory;
import com.codeit.sb13.monew.article.service.rss.client.RestClientRssNewsClient;
import com.codeit.sb13.monew.article.service.rss.config.RssNewsProperties;
import com.codeit.sb13.monew.article.service.rss.mapper.RssNewsMapper;
import com.codeit.sb13.monew.article.service.rss.url.RssFeedUrlResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("external")
@DisplayName("RSS 외부 호출 smoke 테스트")
class RssExternalSmokeTest {

    private final RestClientRssNewsClient client = new RestClientRssNewsClient(
            restClient(),
            new RssNewsProperties(
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(10),
                    new RssNewsProperties.Source("https://www.hankyung.com/feed"),
                    new RssNewsProperties.Source("https://www.chosun.com/arc/outboundfeeds/rss"),
                    new RssNewsProperties.Source("https://www.yonhapnewstv.co.kr")
            ),
            new RssFeedUrlResolver(),
            new RssNewsMapper()
    );

    @Test
    @DisplayName("한국경제 대표 RSS feed를 실제로 호출해 기사 목록을 반환한다")
    void fetchesHankyungDefaultFeedFromExternalSource() {
        assertFeedReturnsArticles(ArticleSource.HANKYUNG, HankyungRssCategory.ALL_NEWS);
    }

    @Test
    @DisplayName("조선일보 대표 RSS feed를 실제로 호출해 기사 목록을 반환한다")
    void fetchesChosunDefaultFeedFromExternalSource() {
        assertFeedReturnsArticles(ArticleSource.CHOSUN, ChosunRssCategory.ALL);
    }

    @Test
    @DisplayName("연합뉴스TV 대표 RSS feed를 실제로 호출해 기사 목록을 반환한다")
    void fetchesYonhapDefaultFeedFromExternalSource() {
        assertFeedReturnsArticles(ArticleSource.YEONHAP, YonhapRssCategory.LATEST);
    }

    private void assertFeedReturnsArticles(ArticleSource source, RssNewsCategory category) {
        List<CollectedArticle> articles = client.fetch(source, category);

        assertThat(articles)
                .as("%s %s RSS articles", source, category.key())
                .isNotEmpty()
                .allSatisfy(article -> {
                    assertThat(article.source()).isEqualTo(source);
                    assertThat(article.title()).isNotBlank();
                    assertThat(article.link()).isNotBlank();
                    assertThat(StringUtils.hasText(article.link())).isTrue();
                });
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .build();
    }
}

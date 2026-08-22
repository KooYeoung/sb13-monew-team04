package com.codeit.sb13.monew.article.service.rss.client;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.rss.category.ChosunRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.HankyungRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.YonhapRssCategory;
import com.codeit.sb13.monew.article.service.rss.config.RssNewsProperties;
import com.codeit.sb13.monew.article.service.rss.mapper.RssNewsMapper;
import com.codeit.sb13.monew.article.service.rss.url.RssFeedUrlResolver;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("RestClientRssNewsClient 단위 테스트")
class RestClientRssNewsClientTest {

    private static final String HANKYUNG_BASE_URL = "https://www.hankyung.com/feed";
    private static final String CHOSUN_BASE_URL = "https://www.chosun.com/arc/outboundfeeds/rss";
    private static final String YONHAP_BASE_URL = "https://www.yonhapnewstv.co.kr";

    private MockRestServiceServer server;
    private RssNewsMapper mapper;
    private RestClientRssNewsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        mapper = mock(RssNewsMapper.class);
        client = new RestClientRssNewsClient(
                builder.build(),
                new RssNewsProperties(
                        null,
                        null,
                        new RssNewsProperties.Source(HANKYUNG_BASE_URL),
                        new RssNewsProperties.Source(CHOSUN_BASE_URL),
                        new RssNewsProperties.Source(YONHAP_BASE_URL)
                ),
                new RssFeedUrlResolver(),
                mapper
        );
    }

    @Test
    @DisplayName("한국경제 RSS를 호출하고 mapper 결과를 반환한다")
    void fetchesHankyungRssThenReturnsMappedArticles() {
        String body = "<rss><channel><item /></channel></rss>";
        List<CollectedArticle> expected = List.of(article(ArticleSource.HANKYUNG));
        when(mapper.toCollectedArticles(ArticleSource.HANKYUNG, body)).thenReturn(expected);

        server.expect(requestTo(HANKYUNG_BASE_URL + "/economy"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        List<CollectedArticle> result = client.fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ECONOMY);

        assertThat(result).isEqualTo(expected);
        verify(mapper).toCollectedArticles(ArticleSource.HANKYUNG, body);
        server.verify();
    }

    @Test
    @DisplayName("조선일보 전체기사 RSS를 호출한다")
    void fetchesChosunAllRss() {
        String body = "<rss />";
        when(mapper.toCollectedArticles(ArticleSource.CHOSUN, body)).thenReturn(List.of());

        server.expect(requestTo(CHOSUN_BASE_URL + "/?outputType=xml"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        List<CollectedArticle> result = client.fetch(ArticleSource.CHOSUN, ChosunRssCategory.ALL);

        assertThat(result).isEmpty();
        verify(mapper).toCollectedArticles(ArticleSource.CHOSUN, body);
        server.verify();
    }

    @Test
    @DisplayName("연합뉴스TV 최신 RSS를 호출한다")
    void fetchesYonhapLatestRss() {
        String body = "<rss />";
        when(mapper.toCollectedArticles(ArticleSource.YEONHAP, body)).thenReturn(List.of());

        server.expect(requestTo(YONHAP_BASE_URL + "/browse/feed/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        List<CollectedArticle> result = client.fetch(ArticleSource.YEONHAP, YonhapRssCategory.LATEST);

        assertThat(result).isEmpty();
        verify(mapper).toCollectedArticles(ArticleSource.YEONHAP, body);
        server.verify();
    }

    @Test
    @DisplayName("HTTP 오류가 발생하면 기사 수집 실패 예외가 발생한다")
    void throwsFetchFailedExceptionWhenHttpErrorOccurs() {
        server.expect(requestTo(HANKYUNG_BASE_URL + "/economy"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ECONOMY))
                .isInstanceOf(ArticleFetchFailedException.class);
        verifyNoInteractions(mapper);
        server.verify();
    }

    @Test
    @DisplayName("요청 timeout이 발생하면 기사 수집 실패 예외가 발생한다")
    void throwsFetchFailedExceptionWhenTimeoutOccurs() {
        server.expect(requestTo(HANKYUNG_BASE_URL + "/economy"))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "Read timed out",
                            new SocketTimeoutException("Read timed out")
                    );
                });

        assertThatThrownBy(() -> client.fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ECONOMY))
                .isInstanceOf(ArticleFetchFailedException.class);
        verifyNoInteractions(mapper);
        server.verify();
    }

    @Test
    @DisplayName("mapper 파싱 예외는 그대로 전파한다")
    void propagatesMapperParseException() {
        String body = "<rss />";
        ArticleFetchParseException exception = new ArticleFetchParseException(
                ArticleSource.HANKYUNG.name(),
                new IllegalArgumentException("invalid xml")
        );
        when(mapper.toCollectedArticles(ArticleSource.HANKYUNG, body)).thenThrow(exception);

        server.expect(requestTo(HANKYUNG_BASE_URL + "/economy"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.fetch(ArticleSource.HANKYUNG, HankyungRssCategory.ECONOMY))
                .isSameAs(exception);
        server.verify();
    }

    @Test
    @DisplayName("RSS 출처가 아니면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenSourceIsNotRssSource() {
        assertThatThrownBy(() -> client.fetch(ArticleSource.NAVER, HankyungRssCategory.ECONOMY))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("source가 null이면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenSourceIsNull() {
        assertThatThrownBy(() -> client.fetch(null, HankyungRssCategory.ECONOMY))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("category가 null이면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenCategoryIsNull() {
        assertThatThrownBy(() -> client.fetch(ArticleSource.HANKYUNG, null))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("source와 category 출처가 다르면 요청 invalid 예외가 발생한다")
    void throwsInvalidRequestWhenSourceAndCategoryDoNotMatch() {
        assertThatThrownBy(() -> client.fetch(ArticleSource.HANKYUNG, ChosunRssCategory.ALL))
                .isInstanceOf(ArticleFetchRequestInvalidException.class);
        verifyNoInteractions(mapper);
        server.verify();
    }

    private CollectedArticle article(ArticleSource source) {
        return new CollectedArticle(
                source,
                "title",
                "summary",
                "https://example.com/article",
                LocalDateTime.of(2026, 8, 21, 10, 0)
        );
    }
}

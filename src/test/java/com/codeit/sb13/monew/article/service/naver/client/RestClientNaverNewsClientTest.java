package com.codeit.sb13.monew.article.service.naver.client;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.config.NaverNewsProperties;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSort;
import com.codeit.sb13.monew.article.service.naver.mapper.NaverNewsMapper;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

@DisplayName("RestClientNaverNewsClient 단위 테스트")
class RestClientNaverNewsClientTest {

    private static final String BASE_URL = "https://openapi.naver.com";
    private static final String PATH = "/v1/search/news.json";
    private static final String CLIENT_ID = "client-id";
    private static final String CLIENT_SECRET = "client-secret";
    private static final String CLIENT_ID_HEADER = "X-Naver-Client-Id";
    private static final String CLIENT_SECRET_HEADER = "X-Naver-Client-Secret";

    private MockRestServiceServer server;
    private RestClientNaverNewsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(CLIENT_ID_HEADER, CLIENT_ID)
                .defaultHeader(CLIENT_SECRET_HEADER, CLIENT_SECRET);

        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientNaverNewsClient(
                builder.build(),
                new NaverNewsProperties(BASE_URL, PATH, CLIENT_ID, CLIENT_SECRET, null, null),
                new NaverNewsMapper(),
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("요청 조건과 인증 헤더를 전송하고 정상 응답을 변환한다")
    void sendsRequestParametersAndHeadersThenConvertsResponse() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("query", "economy"))
                .andExpect(queryParam("display", "7"))
                .andExpect(queryParam("start", "3"))
                .andExpect(queryParam("sort", "date"))
                .andExpect(header(CLIENT_ID_HEADER, CLIENT_ID))
                .andExpect(header(CLIENT_SECRET_HEADER, CLIENT_SECRET))
                .andRespond(withSuccess(successJson(), MediaType.APPLICATION_JSON));

        NaverNewsSearchRequest request = new NaverNewsSearchRequest(
                "economy",
                3,
                7,
                NaverNewsSort.DATE
        );

        // when
        List<CollectedArticle> articles = client.search(request);

        // then
        assertThat(articles).hasSize(1);
        CollectedArticle article = articles.get(0);
        assertThat(article.source()).isEqualTo(ArticleSource.NAVER);
        assertThat(article.title()).isEqualTo("Economy \"News\"");
        assertThat(article.summary()).isEqualTo("Stock & exchange summary");
        assertThat(article.link()).isEqualTo("https://example.com/original?query=economy&page=1");
        assertThat(article.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 10, 15, 30));
        server.verify();
    }

    @Test
    @DisplayName("items가 비어 있으면 빈 목록 반환")
    void returnsEmptyListWhenItemsAreEmpty() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withSuccess(emptyItemsJson(), MediaType.APPLICATION_JSON));

        // when
        List<CollectedArticle> articles = client.search(new NaverNewsSearchRequest("economy"));

        // then
        assertThat(articles).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("401 응답이면 기사 수집 실패 예외 발생")
    void throwsFetchFailedExceptionWhenUnauthorized() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withUnauthorizedRequest());

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchFailedException.class);
        server.verify();
    }

    @Test
    @DisplayName("403 응답이면 기사 수집 실패 예외 발생")
    void throwsFetchFailedExceptionWhenForbidden() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withForbiddenRequest());

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchFailedException.class);
        server.verify();
    }

    @Test
    @DisplayName("5xx 응답이면 기사 수집 실패 예외 발생")
    void throwsFetchFailedExceptionWhenServerError() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchFailedException.class);
        server.verify();
    }

    @Test
    @DisplayName("JSON 파싱에 실패하면 기사 수집 파싱 예외 발생")
    void throwsParseExceptionWhenJsonIsMalformed() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchParseException.class);
        server.verify();
    }

    @Test
    @DisplayName("응답 본문이 null이면 기사 수집 파싱 예외 발생")
    void throwsParseExceptionWhenResponseBodyIsNullLiteral() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchParseException.class);
        server.verify();
    }

    @Test
    @DisplayName("items가 없으면 기사 수집 파싱 예외 발생")
    void throwsParseExceptionWhenItemsAreMissing() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withSuccess(missingItemsJson(), MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchParseException.class);
        server.verify();
    }

    @Test
    @DisplayName("items가 null이면 기사 수집 파싱 예외 발생")
    void throwsParseExceptionWhenItemsAreNull() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withSuccess(nullItemsJson(), MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchParseException.class);
        server.verify();
    }

    @Test
    @DisplayName("요청 timeout이 발생하면 기사 수집 실패 예외 발생")
    void throwsFetchFailedExceptionWhenTimeoutOccurs() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "Read timed out",
                            new SocketTimeoutException("Read timed out")
                    );
                });

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchFailedException.class);
        server.verify();
    }

    @Test
    @DisplayName("pubDate 파싱에 실패하면 기사 수집 파싱 예외 발생")
    void throwsParseExceptionWhenPubDateIsInvalid() {
        // given
        server.expect(requestTo(startsWith(BASE_URL + PATH)))
                .andRespond(withSuccess(invalidPubDateJson(), MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.search(new NaverNewsSearchRequest("economy")))
                .isInstanceOf(ArticleFetchParseException.class);
        server.verify();
    }

    private String successJson() {
        return """
                {
                  "lastBuildDate": "Thu, 20 Aug 2026 10:16:00 +0900",
                  "total": 1,
                  "start": 3,
                  "display": 7,
                  "items": [
                    {
                      "title": "<b>Economy</b> &quot;News&quot;",
                      "originallink": "https://example.com/original?query=economy&amp;page=1",
                      "link": "https://n.news.naver.com/article/001/0000000001",
                      "description": "<b>Stock</b> &amp; exchange summary",
                      "pubDate": "Thu, 20 Aug 2026 10:15:30 +0900"
                    }
                  ]
                }
                """;
    }

    private String emptyItemsJson() {
        return """
                {
                  "lastBuildDate": "Thu, 20 Aug 2026 10:16:00 +0900",
                  "total": 0,
                  "start": 1,
                  "display": 10,
                  "items": []
                }
                """;
    }

    private String missingItemsJson() {
        return """
                {
                  "lastBuildDate": "Thu, 20 Aug 2026 10:16:00 +0900",
                  "total": 0,
                  "start": 1,
                  "display": 10
                }
                """;
    }

    private String nullItemsJson() {
        return """
                {
                  "lastBuildDate": "Thu, 20 Aug 2026 10:16:00 +0900",
                  "total": 0,
                  "start": 1,
                  "display": 10,
                  "items": null
                }
                """;
    }

    private String invalidPubDateJson() {
        return """
                {
                  "lastBuildDate": "Thu, 20 Aug 2026 10:16:00 +0900",
                  "total": 1,
                  "start": 1,
                  "display": 10,
                  "items": [
                    {
                      "title": "Economy",
                      "originallink": "https://example.com/original",
                      "link": "https://n.news.naver.com/article/001/0000000001",
                      "description": "summary",
                      "pubDate": "invalid-date"
                    }
                  ]
                }
                """;
    }
}

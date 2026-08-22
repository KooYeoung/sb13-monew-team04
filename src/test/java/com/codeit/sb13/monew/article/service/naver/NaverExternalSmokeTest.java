package com.codeit.sb13.monew.article.service.naver;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.client.RestClientNaverNewsClient;
import com.codeit.sb13.monew.article.service.naver.config.NaverNewsProperties;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSort;
import com.codeit.sb13.monew.article.service.naver.mapper.NaverNewsMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("external")
@DisplayName("NAVER 뉴스 외부 호출 smoke 테스트")
class NaverExternalSmokeTest {

    private static final String BASE_URL = "https://openapi.naver.com";
    private static final String PATH = "/v1/search/news.json";
    private static final String CLIENT_ID_ENV = "MONEW_NAVER_CLIENT_ID";
    private static final String CLIENT_SECRET_ENV = "MONEW_NAVER_CLIENT_SECRET";
    private static final String CLIENT_ID_HEADER = "X-Naver-Client-Id";
    private static final String CLIENT_SECRET_HEADER = "X-Naver-Client-Secret";

    @Test
    @DisplayName("NAVER 뉴스 API를 실제로 호출해 기사 목록을 반환한다")
    void fetchesNewsFromExternalNaverApi() {
        Map<String, String> envDev = loadEnvDev();
        String clientId = credential(CLIENT_ID_ENV, envDev);
        String clientSecret = credential(CLIENT_SECRET_ENV, envDev);
        assumeTrue(StringUtils.hasText(clientId), CLIENT_ID_ENV + " 환경변수가 없으면 외부 smoke 테스트를 건너뜁니다.");
        assumeTrue(StringUtils.hasText(clientSecret), CLIENT_SECRET_ENV + " 환경변수가 없으면 외부 smoke 테스트를 건너뜁니다.");

        RestClientNaverNewsClient client = new RestClientNaverNewsClient(
                restClient(clientId, clientSecret),
                new NaverNewsProperties(
                        BASE_URL,
                        PATH,
                        clientId,
                        clientSecret,
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10)
                ),
                new NaverNewsMapper(),
                new ObjectMapper()
        );

        List<CollectedArticle> articles = client.search(
                new NaverNewsSearchRequest("경제", NaverNewsSort.DATE, 5)
        );

        assertThat(articles)
                .isNotEmpty()
                .allSatisfy(article -> {
                    assertThat(article.source()).isEqualTo(ArticleSource.NAVER);
                    assertThat(article.title()).isNotBlank();
                    assertThat(article.link()).isNotBlank();
                    assertThat(article.publishedAt()).isNotNull();
                });
    }

    private RestClient restClient(String clientId, String clientSecret) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(CLIENT_ID_HEADER, clientId)
                .defaultHeader(CLIENT_SECRET_HEADER, clientSecret)
                .requestFactory(requestFactory)
                .build();
    }

    private String credential(String key, Map<String, String> envDev) {
        String value = System.getenv(key);
        if (StringUtils.hasText(value)) {
            return value;
        }

        return envDev.get(key);
    }

    private Map<String, String> loadEnvDev() {
        Path envDevPath = Path.of(".env.dev");
        if (!Files.isRegularFile(envDevPath)) {
            return Map.of();
        }

        try {
            Map<String, String> values = new HashMap<>();
            Files.readAllLines(envDevPath).forEach(line -> putEnvValue(values, line));
            return values;
        } catch (IOException e) {
            return Map.of();
        }
    }

    private void putEnvValue(Map<String, String> values, String line) {
        String normalizedLine = line.strip();
        if (!StringUtils.hasText(normalizedLine) || normalizedLine.startsWith("#")) {
            return;
        }
        if (normalizedLine.startsWith("export ")) {
            normalizedLine = normalizedLine.substring("export ".length()).strip();
        }

        int separatorIndex = normalizedLine.indexOf('=');
        if (separatorIndex < 0) {
            return;
        }

        String key = normalizedLine.substring(0, separatorIndex).strip();
        String value = stripQuotes(normalizedLine.substring(separatorIndex + 1).strip());
        if (StringUtils.hasText(key)) {
            values.put(key, value);
        }
    }

    private String stripQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}

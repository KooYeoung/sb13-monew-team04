package com.codeit.sb13.monew.article.service.naver.client;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.config.NaverNewsProperties;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchRequest;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchResponse;
import com.codeit.sb13.monew.article.service.naver.mapper.NaverNewsMapper;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;


@Component
@RequiredArgsConstructor
public class RestClientNaverNewsClient implements NaverNewsClient {
    private static final String SOURCE = ArticleSource.NAVER.name();
    private static final String RESPONSE_FORMAT = "json";

    private final RestClient naverNewsRestClient;
    private final NaverNewsProperties props;
    private final NaverNewsMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<CollectedArticle> search(NaverNewsSearchRequest request) {
        try {
            String body = naverNewsRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(props.path())
                            .queryParam("query", request.query())
                            .queryParam("display", request.display())
                            .queryParam("start", request.start())
                            .queryParam("sort", request.sort().getValue())
                            .queryParam("format", RESPONSE_FORMAT)
                            .build()
                    )
                    .retrieve()
                    .body(String.class);

            NaverNewsSearchResponse response = parseResponse(body);

            return mapper.toCollectedArticles(response);
        } catch (ArticleFetchParseException e) {
            throw e;
        } catch (JacksonException e) {
            throw new ArticleFetchParseException(SOURCE, e);
        } catch (RestClientException e) {
            throw new ArticleFetchFailedException(SOURCE, e);
        }
    }

    private NaverNewsSearchResponse parseResponse(String body) throws JacksonException {
        if (body == null) {
            throw invalidResponse();
        }

        NaverNewsSearchResponse response = objectMapper.readValue(body, NaverNewsSearchResponse.class);
        if (response == null || response.items() == null) {
            throw invalidResponse();
        }

        return response;
    }

    private ArticleFetchParseException invalidResponse() {
        return new ArticleFetchParseException(
                SOURCE,
                new IllegalArgumentException("Invalid NAVER news response schema")
        );
    }
}

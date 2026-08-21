package com.codeit.sb13.monew.article.service.naver.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NaverNewsSearchResponse 단위 테스트")
class NaverNewsSearchResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("NAVER 뉴스 검색 JSON 응답을 매핑한다")
    void mapsNaverNewsSearchJsonResponse() throws Exception {
        // given
        String json = """
                {
                  "lastBuildDate": "Tue, 14 Oct 2025 10:00:00 +0900",
                  "total": 120,
                  "start": 1,
                  "display": 10,
                  "items": [
                    {
                      "title": "<b>경제</b> 뉴스 제목",
                      "originallink": "https://example.com/original-news",
                      "link": "https://n.news.naver.com/article/001/0000000001",
                      "description": "<b>경제</b> 뉴스 요약",
                      "pubDate": "Tue, 14 Oct 2025 09:30:00 +0900"
                    }
                  ]
                }
                """;

        // when
        NaverNewsSearchResponse response = objectMapper.readValue(json, NaverNewsSearchResponse.class);

        // then
        assertThat(response.lastBuildDate()).isEqualTo("Tue, 14 Oct 2025 10:00:00 +0900");
        assertThat(response.total()).isEqualTo(120);
        assertThat(response.start()).isEqualTo(1);
        assertThat(response.display()).isEqualTo(10);
        assertThat(response.items()).hasSize(1);

        NaverNewsItem item = response.items().get(0);
        assertThat(item.title()).isEqualTo("<b>경제</b> 뉴스 제목");
        assertThat(item.originallink()).isEqualTo("https://example.com/original-news");
        assertThat(item.link()).isEqualTo("https://n.news.naver.com/article/001/0000000001");
        assertThat(item.description()).isEqualTo("<b>경제</b> 뉴스 요약");
        assertThat(item.pubDate()).isEqualTo("Tue, 14 Oct 2025 09:30:00 +0900");
    }
}

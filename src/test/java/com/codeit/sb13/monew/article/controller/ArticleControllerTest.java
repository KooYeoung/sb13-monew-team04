package com.codeit.sb13.monew.article.controller;

import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleSearchCommand;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.article.ArticleViewConflictException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.codeit.sb13.monew.global.MonewHttpHeaders.REQUEST_USER_ID;

@WebMvcTest(ArticleController.class)
@DisplayName("ArticleController 슬라이스 테스트")
class ArticleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ArticleService articleService;

    @MockitoBean
    ArticleViewService articleViewService;

    private UUID articleId;
    private UUID userId;
    private ArticleDto articleDto;
    private ArticleViewDto articleViewDto;

    @BeforeEach
    void setUp() {
        articleId = UUID.randomUUID();
        userId = UUID.randomUUID();

        articleDto = new ArticleDto(
                articleId,
                ArticleSource.NAVER,
                "https://example.com/article",
                "Test Article",
                LocalDateTime.of(2026, 8, 20, 10, 0),
                "Test Summary",
                0L,
                7L,
                true
        );

        articleViewDto = new ArticleViewDto(
                UUID.randomUUID(),
                userId,
                LocalDateTime.of(2026, 8, 21, 9, 0),
                articleId,
                ArticleSource.NAVER,
                "https://example.com/article",
                "Test Article",
                LocalDateTime.of(2026, 8, 20, 10, 0),
                "Test Summary",
                0L,
                1L
        );
    }

    @Test
    @DisplayName("단건 조회 성공 시 200과 ArticleDto를 반환한다")
    void getArticleSuccess() throws Exception {
        // given
        when(articleService.getArticle(articleId, userId)).thenReturn(articleDto);

        // when & then
        mockMvc.perform(get("/api/articles/{articleId}", articleId)
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.source").value("NAVER"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/article"))
                .andExpect(jsonPath("$.title").value("Test Article"))
                .andExpect(jsonPath("$.summary").value("Test Summary"))
                .andExpect(jsonPath("$.viewCount").value(7))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.viewedByMe").value(true));

        verify(articleService).getArticle(articleId, userId);
    }

    @Test
    @DisplayName("존재하지 않는 기사는 404와 ART_001을 반환한다")
    void getArticleNotFound() throws Exception {
        // given
        when(articleService.getArticle(articleId, userId))
                .thenThrow(new ArticleNotFoundException(articleId));

        // when & then
        mockMvc.perform(get("/api/articles/{articleId}", articleId)
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ART_001"))
                .andExpect(jsonPath("$.details.articleId").value(articleId.toString()));
    }

    @Test
    @DisplayName("Monew-Request-User-ID 헤더가 없으면 400을 반환한다")
    void getArticleWithoutHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/articles/{articleId}", articleId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleService, never()).getArticle(any(), any());
    }

    @Test
    @DisplayName("잘못된 UUID 형식의 기사 ID는 400을 반환한다")
    void getArticleWithInvalidUuid() throws Exception {
        // when & then
        mockMvc.perform(get("/api/articles/not-a-uuid")
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleService, never()).getArticle(any(), any());
    }

    @Test
    @DisplayName("출처 목록 조회 시 200과 enum 값 전체를 반환한다")
    void getSourcesSuccess() throws Exception {
        // given
        when(articleService.getSources()).thenReturn(List.of(ArticleSource.values()));

        // when & then
        mockMvc.perform(get("/api/articles/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0]").value("NAVER"))
                .andExpect(jsonPath("$[1]").value("HANKYUNG"))
                .andExpect(jsonPath("$[2]").value("CHOSUN"))
                .andExpect(jsonPath("$[3]").value("YEONHAP"));

        verify(articleService, never()).getArticle(any(), any());
    }

    @Test
    @DisplayName("잘못된 UUID 형식의 요청자 헤더는 400을 반환한다")
    void getArticleWithInvalidHeaderUuid() throws Exception {
        mockMvc.perform(get("/api/articles/{articleId}", articleId)
                        .header(REQUEST_USER_ID, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleService, never()).getArticle(any(), any());
    }

    @Test
    @DisplayName("뷰 등록 성공 시 200과 ArticleViewDto를 반환한다")
    void registerArticleViewSuccess() throws Exception {
        // given
        when(articleViewService.recordView(articleId, userId)).thenReturn(articleViewDto);

        // when & then
        mockMvc.perform(post("/api/articles/{articleId}/article-views", articleId)
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewedBy").value(userId.toString()))
                .andExpect(jsonPath("$.articleId").value(articleId.toString()))
                .andExpect(jsonPath("$.source").value("NAVER"))
                .andExpect(jsonPath("$.articleTitle").value("Test Article"))
                .andExpect(jsonPath("$.articleSummary").value("Test Summary"))
                .andExpect(jsonPath("$.articleViewCount").value(1))
                .andExpect(jsonPath("$.articleCommentCount").value(0));

        verify(articleViewService).recordView(articleId, userId);
    }

    @Test
    @DisplayName("뷰 등록 시 존재하지 않는 기사는 404와 ART_001을 반환한다")
    void registerArticleViewArticleNotFound() throws Exception {
        // given
        when(articleViewService.recordView(articleId, userId))
                .thenThrow(new ArticleNotFoundException(articleId));

        // when & then
        mockMvc.perform(post("/api/articles/{articleId}/article-views", articleId)
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ART_001"))
                .andExpect(jsonPath("$.details.articleId").value(articleId.toString()));
    }

    @Test
    @DisplayName("뷰 등록 시 존재하지 않는 사용자는 404와 USR_001을 반환한다")
    void registerArticleViewUserNotFound() throws Exception {
        // given
        when(articleViewService.recordView(articleId, userId))
                .thenThrow(new UserNotFoundException(userId));

        // when & then
        mockMvc.perform(post("/api/articles/{articleId}/article-views", articleId)
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USR_001"))
                .andExpect(jsonPath("$.details.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("뷰 등록 시 Monew-Request-User-ID 헤더가 없으면 400을 반환한다")
    void registerArticleViewWithoutHeader() throws Exception {
        // when & then
        mockMvc.perform(post("/api/articles/{articleId}/article-views", articleId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleViewService, never()).recordView(any(), any());
    }

    @Test
    @DisplayName("뷰 등록 중 동시 요청 충돌 시 409와 ART_006을 반환한다")
    void registerArticleViewConflict() throws Exception {
        // given
        when(articleViewService.recordView(articleId, userId))
                .thenThrow(new ArticleViewConflictException());

        // when & then
        mockMvc.perform(post("/api/articles/{articleId}/article-views", articleId)
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ART_006"));
    }

    @Test
    @DisplayName("목록 조회 성공 시 200과 ArticleDto 배열을 반환한다")
    void getArticlesSuccess() throws Exception {
        // given
        when(articleService.searchArticles(any(ArticleSearchCommand.class)))
                .thenReturn(List.of(articleDto));

        // when & then
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(articleId.toString()))
                .andExpect(jsonPath("$[0].source").value("NAVER"))
                .andExpect(jsonPath("$[0].title").value("Test Article"))
                .andExpect(jsonPath("$[0].viewCount").value(7))
                .andExpect(jsonPath("$[0].commentCount").value(0))
                .andExpect(jsonPath("$[0].viewedByMe").value(true));
    }

    @Test
    @DisplayName("목록 조회 - 쿼리 파라미터가 검색 커맨드로 전달된다")
    void getArticlesBindsQueryParameters() throws Exception {
        // given
        when(articleService.searchArticles(any(ArticleSearchCommand.class)))
                .thenReturn(List.of());

        // when
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("keyword", "반도체")
                        .param("sourceIn", "NAVER", "CHOSUN")
                        .param("publishDateFrom", "2026-08-01T00:00:00")
                        .param("publishDateTo", "2026-08-31T00:00:00"))
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<ArticleSearchCommand> captor =
                ArgumentCaptor.forClass(ArticleSearchCommand.class);
        verify(articleService).searchArticles(captor.capture());

        ArticleSearchCommand command = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.keyword()).isEqualTo("반도체");
        org.assertj.core.api.Assertions.assertThat(command.sourceIn())
                .containsExactly(ArticleSource.NAVER, ArticleSource.CHOSUN);
        org.assertj.core.api.Assertions.assertThat(command.publishDateFrom())
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        org.assertj.core.api.Assertions.assertThat(command.publishDateTo())
                .isEqualTo(LocalDateTime.of(2026, 8, 31, 0, 0));
        org.assertj.core.api.Assertions.assertThat(command.requestUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("목록 조회 시 Monew-Request-User-ID 헤더가 없으면 400을 반환한다")
    void getArticlesWithoutHeader() throws Exception {
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleService, never()).searchArticles(any());
    }

    @Test
    @DisplayName("목록 조회 시 정의되지 않은 출처 값은 400을 반환한다")
    void getArticlesWithInvalidSource() throws Exception {
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("sourceIn", "DAUM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleService, never()).searchArticles(any());
    }
}
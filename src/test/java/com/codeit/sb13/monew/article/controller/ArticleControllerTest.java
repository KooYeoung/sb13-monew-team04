package com.codeit.sb13.monew.article.controller;

import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.article.service.dto.ArticleDto;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.s3.service.ArticleRestoreService;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreCommand;
import com.codeit.sb13.monew.article.s3.service.dto.ArticleRestoreResult;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import com.codeit.sb13.monew.article.service.dto.ArticleSearchCommand;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.article.ArticleRestoreFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.article.ArticleViewConflictException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.codeit.sb13.monew.global.MonewHttpHeaders.REQUEST_USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.Mockito.doThrow;

@WebMvcTest(ArticleController.class)
@DisplayName("ArticleController 슬라이스 테스트")
class ArticleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ArticleService articleService;

    @MockitoBean
    ArticleViewService articleViewService;

    @MockitoBean
    ArticleRestoreService articleRestoreService;

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
    @DisplayName("목록 조회 성공 시 200과 커서 페이지 응답을 반환한다")
    void getArticlesSuccess() throws Exception {
        // given
        when(articleService.searchArticles(any(ArticleSearchCommand.class)))
                .thenReturn(new CursorPageResponseDto<>(
                        List.of(articleDto), "2026-08-20T12:00", "2026-08-20T09:00",
                        articleId.toString(), 1, 42L, true));

        // when & then
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("orderBy", "publishDate")
                        .param("direction", "DESC")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(articleId.toString()))
                .andExpect(jsonPath("$.content[0].source").value("NAVER"))
                .andExpect(jsonPath("$.content[0].title").value("Test Article"))
                .andExpect(jsonPath("$.content[0].viewCount").value(7))
                .andExpect(jsonPath("$.content[0].commentCount").value(0))
                .andExpect(jsonPath("$.content[0].viewedByMe").value(true))
                .andExpect(jsonPath("$.nextCursor").value("2026-08-20T12:00"))
                .andExpect(jsonPath("$.nextIdAfter").value(articleId.toString()))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("목록 조회 - 쿼리 파라미터가 검색 커맨드로 전달된다")
    void getArticlesBindsQueryParameters() throws Exception {
        // given
        when(articleService.searchArticles(any(ArticleSearchCommand.class)))
                .thenReturn(new CursorPageResponseDto<>(
                        List.of(), null, null, null, 0, 0L, false));

        // when
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("keyword", "반도체")
                        .param("sourceIn", "NAVER", "CHOSUN")
                        .param("publishDateFrom", "2026-08-01T00:00:00")
                        .param("publishDateTo", "2026-08-31T00:00:00")
                        .param("orderBy", "viewCount")
                        .param("direction", "ASC")
                        .param("cursor", "10")
                        .param("after", "2026-08-20T12:00:00")
                        .param("idAfter", "11111111-1111-1111-1111-111111111111")
                        .param("limit", "30"))
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
        org.assertj.core.api.Assertions.assertThat(command.orderBy())
                .isEqualTo(ArticleOrderBy.VIEW_COUNT);
        org.assertj.core.api.Assertions.assertThat(command.direction())
                .isEqualTo(Sort.Direction.ASC);
        org.assertj.core.api.Assertions.assertThat(command.cursor()).isEqualTo("10");
        org.assertj.core.api.Assertions.assertThat(command.after())
                .isEqualTo(LocalDateTime.of(2026, 8, 20, 12, 0));
        org.assertj.core.api.Assertions.assertThat(command.idAfter())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        org.assertj.core.api.Assertions.assertThat(command.limit()).isEqualTo(30);
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

    @Test
    @DisplayName("기사 복구 성공 시 200과 날짜별 복구 결과를 반환한다")
    void restoreArticlesSuccess() throws Exception {
        LocalDate restoreFrom = LocalDate.of(2026, 8, 10);
        LocalDate restoreTo = LocalDate.of(2026, 8, 27);
        UUID restoredArticleId = UUID.randomUUID();
        ArticleRestoreResult restoreResult = ArticleRestoreResult.of(restoreFrom, List.of(restoredArticleId));
        when(articleRestoreService.restoreArticles(any(ArticleRestoreCommand.class)))
                .thenReturn(List.of(restoreResult));

        mockMvc.perform(get("/api/articles/restore")
                        .param("from", "2026-08-10T00:00:00")
                        .param("to", "2026-08-27T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].restoreDate").value("2026-08-10"))
                .andExpect(jsonPath("$[0].restoredArticleIds[0]").value(restoredArticleId.toString()))
                .andExpect(jsonPath("$[0].restoredArticleCount").value(1));

        ArgumentCaptor<ArticleRestoreCommand> captor = ArgumentCaptor.forClass(ArticleRestoreCommand.class);
        verify(articleRestoreService).restoreArticles(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(restoreFrom);
        assertThat(captor.getValue().to()).isEqualTo(restoreTo);
    }

    @Test
    @DisplayName("기사 복구 요청에서 시작일이 종료일보다 이후이면 400을 반환한다")
    void restoreArticlesReturnsBadRequestWhenDateRangeIsInvalid() throws Exception {
        mockMvc.perform(get("/api/articles/restore")
                        .param("from", "2026-08-24T00:00:00")
                        .param("to", "2026-08-23T23:59:59"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleRestoreService, never()).restoreArticles(any());
    }

    @Test
    @DisplayName("기사 복구 실패 시 500과 ART_015를 반환한다")
    void restoreArticlesReturnsServerErrorWhenRestoreFails() throws Exception {
        LocalDate restoreDate = LocalDate.of(2026, 8, 23);
        when(articleRestoreService.restoreArticles(any(ArticleRestoreCommand.class)))
                .thenThrow(new ArticleRestoreFailedException(restoreDate, new IllegalStateException("restore failure")));

        mockMvc.perform(get("/api/articles/restore")
                        .param("from", "2026-08-23T00:00:00")
                        .param("to", "2026-08-23T23:59:59"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ART_015"))
                .andExpect(jsonPath("$.details.restoreDate").value("2026-08-23"));
    }

    @Test
    @DisplayName("논리 삭제 성공 시 204를 반환한다")
    void softDeleteArticle() throws Exception {
        mockMvc.perform(delete("/api/articles/{articleId}", articleId))
                .andExpect(status().isNoContent());

        verify(articleService).softDelete(articleId);
    }

    @Test
    @DisplayName("논리 삭제 시 기사가 없으면 404와 ART_001을 반환한다")
    void softDeleteArticleNotFound() throws Exception {
        doThrow(new ArticleNotFoundException(articleId))
                .when(articleService).softDelete(articleId);

        mockMvc.perform(delete("/api/articles/{articleId}", articleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ART_001"));
    }

    @Test
    @DisplayName("물리 삭제 성공 시 204를 반환한다")
    void hardDeleteArticle() throws Exception {
        mockMvc.perform(delete("/api/articles/{articleId}/hard", articleId))
                .andExpect(status().isNoContent());

        verify(articleService).hardDelete(articleId);
    }

    @Test
    @DisplayName("물리 삭제 시 기사가 없으면 404와 ART_001을 반환한다")
    void hardDeleteArticleNotFound() throws Exception {
        doThrow(new ArticleNotFoundException(articleId))
                .when(articleService).hardDelete(articleId);

        mockMvc.perform(delete("/api/articles/{articleId}/hard", articleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ART_001"));
    }

    @Test
    @DisplayName("삭제 시 잘못된 UUID 형식이면 400을 반환한다")
    void deleteArticleWithInvalidUuid() throws Exception {
        mockMvc.perform(delete("/api/articles/{articleId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLB_001"));

        verify(articleService, never()).softDelete(any(UUID.class));
    }


    @Test
    @DisplayName("목록 조회 시 orderBy가 없으면 400과 ART_016을 반환한다")
    void getArticlesWithoutOrderBy() throws Exception {
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("direction", "DESC")
                        .param("limit", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ART_016"));

        verify(articleService, never()).searchArticles(any());
    }

    @Test
    @DisplayName("목록 조회 시 direction이 없으면 400과 ART_016을 반환한다")
    void getArticlesWithoutDirection() throws Exception {
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("orderBy", "publishDate")
                        .param("limit", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ART_016"));

        verify(articleService, never()).searchArticles(any());
    }

    @Test
    @DisplayName("목록 조회 시 limit이 1 미만이면 400과 ART_016을 반환한다")
    void getArticlesWithInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("orderBy", "publishDate")
                        .param("direction", "DESC")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ART_016"));

        verify(articleService, never()).searchArticles(any());
    }

    @Test
    @DisplayName("목록 조회 시 정의되지 않은 orderBy 값은 400을 반환한다")
    void getArticlesWithUnknownOrderBy() throws Exception {
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("orderBy", "randomField")
                        .param("direction", "DESC")
                        .param("limit", "50"))
                .andExpect(status().isBadRequest());

        verify(articleService, never()).searchArticles(any());
    }


    @Test
    @DisplayName("목록 조회 시 limit이 상한을 넘으면 400과 ART_016을 반환한다")
    void getArticlesWithLimitAboveMax() throws Exception {
        mockMvc.perform(get("/api/articles")
                        .header(REQUEST_USER_ID, userId)
                        .param("orderBy", "publishDate")
                        .param("direction", "DESC")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ART_016"));

        verify(articleService, never()).searchArticles(any());
    }
}

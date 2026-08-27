package com.codeit.sb13.monew.comment.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.CommentOrderBy;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CursorPageResponseCommentDto;
import com.codeit.sb13.monew.global.exception.comment.CommentNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(CommentController.class)
@DisplayName("댓글 컨트롤러 - TDD")
public class CommentControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  private CommentService commentService;

  private MockHttpServletRequestBuilder commentSearchRequestWithRequestUserHeader() {
    return get("/api/comments")
        .header("Monew-Request-User-ID", UUID.randomUUID());
  }

  @Test
  @DisplayName("댓글 생성 성공 - GREEN")
  void 댓글_생성_성공() throws Exception {
    // given
    Article article = Article.create("기사 제목", "기사 요약", "https://test.com/article",
        LocalDateTime.now(), ArticleSource.NAVER);
    User user = User.builder()
        .email("test@test.com")
        .nickname("사용자 닉네임")
        .password("Abcd!")
        .build();
    String content = "테스트 댓글";
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID()); // user 객체에 id 필드 설정
    ReflectionTestUtils.setField(article, "id", UUID.randomUUID()); // article 객체에 id 필드 설정

    CommentDto response=new CommentDto(UUID.randomUUID(), article.getId(), user.getId(), user.getNickname(), content, 0L, false, LocalDateTime.now());
    given(commentService.create(argThat(command->command != null
        && article.getId().equals(command.articleId())
        && user.getId().equals(command.userId())
        && content.equals(command.content())))).willReturn(response); // 값이 정확히 일치하는 방향만 통과하도록 테스트 수정

    mockMvc.perform(
        post("/api/comments")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "articleId": "%s",
                  "userId": "%s",
                  "content": "%s"
                }
                """.formatted(article.getId(), user.getId(), content)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(response.id().toString()))
        .andExpect(jsonPath("$.articleId").value(article.getId().toString()))
        .andExpect(jsonPath("$.userId").value(user.getId().toString()))
        .andExpect(jsonPath("$.content").value(content))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.userNickname").value("사용자 닉네임"))
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.likedByMe").value(false));
  }

  @Test
  @DisplayName("댓글 목록 조회 성공 - GREEN")
  void 댓글_목록_조회_성공() throws Exception {
    // given
    UUID articleId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.of(2026, 8, 25, 10, 30);
    CommentDto comment = new CommentDto(
        commentId, articleId, UUID.randomUUID(), "작성자", "댓글 내용", 2L, true, createdAt);
    CursorPageResponseCommentDto response = new CursorPageResponseCommentDto(
        List.of(comment), commentId.toString(), createdAt.toString(), 1, 1L, false);

    given(commentService.search(argThat(command -> command != null
        && articleId.equals(command.articleId())
        && command.orderBy() == CommentOrderBy.CREATED_AT
        && command.direction().isDescending()
        && command.cursor() == null
        && command.after() == null
        && command.limit() == 10
        && requestUserId.equals(command.requestUserId())))).willReturn(response);

    // when & then
    mockMvc.perform(get("/api/comments")
            .param("articleId", articleId.toString())
            .param("orderBy", "createdAt")
            .param("direction", "DESC")
            .param("limit", "10")
            .header("Monew-Request-User-ID", requestUserId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(commentId.toString()))
        .andExpect(jsonPath("$.content[0].likeCount").value(2))
        .andExpect(jsonPath("$.content[0].likedByMe").value(true))
        .andExpect(jsonPath("$.nextCursor").value(commentId.toString()))
        .andExpect(jsonPath("$.nextAfter").value(createdAt.toString()))
        .andExpect(jsonPath("$.nextIdAfter").doesNotExist())
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("완전한 cursor + after 값을 댓글 목록 조회 명령으로 전달한다")
  void 댓글_목록_조회_명령으로_완전한_커서_값을_전달한다() throws Exception {
    UUID articleId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    UUID cursor = UUID.randomUUID();
    LocalDateTime after = LocalDateTime.of(2026, 8, 25, 10, 30);
    CursorPageResponseCommentDto response = new CursorPageResponseCommentDto(
        List.of(), null, null, 0, 0L, false);

    given(commentService.search(argThat(command -> command != null
        && articleId.equals(command.articleId())
        && command.orderBy() == CommentOrderBy.CREATED_AT
        && command.direction().isAscending()
        && cursor.toString().equals(command.cursor())
        && after.equals(command.after())
        && command.limit() == 10
        && requestUserId.equals(command.requestUserId())))).willReturn(response);

    mockMvc.perform(get("/api/comments")
            .param("articleId", articleId.toString())
            .param("orderBy", "createdAt")
            .param("direction", "ASC")
            .param("cursor", cursor.toString())
            .param("after", after.toString())
            .param("limit", "10")
            .header("Monew-Request-User-ID", requestUserId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.hasNext").value(false));
  }


  @Test
  @DisplayName("cursor만 전달해도 댓글 목록 조회 명령으로 전달한다")
  void cursor만_전달시_댓글_목록_조회_성공() throws Exception {
    UUID cursor = UUID.randomUUID();
    given(commentService.search(argThat(command -> cursor.toString().equals(command.cursor())
        && command.after() == null))).willReturn(
        new CursorPageResponseCommentDto(List.of(), null, null, 0, 0L, false));

    mockMvc.perform(get("/api/comments")
            .param("articleId", UUID.randomUUID().toString())
            .param("orderBy", "likeCount")
            .param("direction", "DESC")
            .param("cursor", cursor.toString())
            .param("limit", "10")
            .header("Monew-Request-User-ID", UUID.randomUUID()))
        .andExpect(status().isOk());

    then(commentService).should().search(argThat(command -> cursor.toString().equals(command.cursor())
        && command.after() == null));
  }

  @Test
  @DisplayName("cursor와 after만 전달하면 댓글 목록 조회에 성공한다")
  void cursor와_after만_전달하면_댓글_목록_조회_성공() throws Exception {
    UUID articleId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    UUID cursor = UUID.randomUUID();
    LocalDateTime after = LocalDateTime.of(2026, 8, 25, 10, 30);
    given(commentService.search(any())).willReturn(
        new CursorPageResponseCommentDto(List.of(), null, null, 0, 0L, false));
    mockMvc.perform(get("/api/comments")
            .param("articleId", articleId.toString())
            .param("orderBy", "likeCount")
            .param("direction", "DESC")
            .param("cursor", cursor.toString())
            .param("after", after.toString())
            .param("limit", "10")
            .header("Monew-Request-User-ID", requestUserId))
        .andExpect(status().isOk());

    then(commentService).should().search(argThat(command -> cursor.toString().equals(command.cursor())
        && after.equals(command.after())));
  }

  @Test
  @DisplayName("articleId 없이 댓글 목록을 조회하면 전체 댓글 목록 조회 명령을 전달한다")
  void articleId_없이_댓글_목록_조회시_성공() throws Exception {
    given(commentService.search(argThat(command -> command.articleId() == null)))
        .willReturn(new CursorPageResponseCommentDto(List.of(), null, null, 0, 0L, false));

    mockMvc.perform(commentSearchRequestWithRequestUserHeader()
            .param("orderBy", "createdAt")
            .param("direction", "DESC")
            .param("limit", "10"))
        .andExpect(status().isOk());

    then(commentService).should().search(argThat(command -> command.articleId() == null));
  }

  @Test
  @DisplayName("orderBy 없이 댓글 목록을 조회하면 실패한다")
  void orderBy_없이_댓글_목록_조회시_실패() throws Exception {
    mockMvc.perform(commentSearchRequestWithRequestUserHeader()
            .param("articleId", UUID.randomUUID().toString())
            .param("direction", "DESC")
            .param("limit", "10"))
        .andExpect(status().isBadRequest());

    then(commentService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("direction 없이 댓글 목록을 조회하면 실패한다")
  void direction_없이_댓글_목록_조회시_실패() throws Exception {
    mockMvc.perform(commentSearchRequestWithRequestUserHeader()
            .param("articleId", UUID.randomUUID().toString())
            .param("orderBy", "createdAt")
            .param("limit", "10"))
        .andExpect(status().isBadRequest());

    then(commentService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("limit이 0이면 댓글 목록 조회에 실패한다")
  void limit이_0이면_댓글_목록_조회시_실패() throws Exception {
    mockMvc.perform(commentSearchRequestWithRequestUserHeader()
            .param("articleId", UUID.randomUUID().toString())
            .param("orderBy", "createdAt")
            .param("direction", "DESC")
            .param("limit", "0"))
        .andExpect(status().isBadRequest());

    then(commentService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("지원하지 않는 정렬 기준이면 댓글 목록 조회에 실패한다")
  void 지원하지_않는_정렬_기준이면_댓글_목록_조회_실패() throws Exception {
    mockMvc.perform(get("/api/comments")
            .param("articleId", UUID.randomUUID().toString())
            .param("orderBy", "unknown")
            .param("direction", "DESC")
            .param("limit", "10")
            .header("Monew-Request-User-ID", UUID.randomUUID()))
        .andExpect(status().isBadRequest());

    then(commentService).shouldHaveNoInteractions();
  }


  @Test
  @DisplayName("댓글 정보 수정 성공 - GREEN")
  void 댓글_수정_성공() throws Exception {
    UUID commentId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();
    LocalDateTime createdAt = LocalDateTime.of(2026, 8, 25, 10, 30);
    CommentDto response = new CommentDto(commentId, UUID.randomUUID(), requestUserId,
        "작성자", "수정된 댓글", 2L, true, createdAt);
    given(commentService.update(argThat(command -> command != null
        && commentId.equals(command.commentId())
        && requestUserId.equals(command.requestUserId())
        && "수정된 댓글".equals(command.content())))).willReturn(response);

    mockMvc.perform(patch("/api/comments/{commentId}", commentId)
            .header("Monew-Request-User-ID", requestUserId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"수정된 댓글\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(commentId.toString()))
        .andExpect(jsonPath("$.content").value("수정된 댓글"))
        .andExpect(jsonPath("$.likeCount").value(2))
        .andExpect(jsonPath("$.likedByMe").value(true));
  }

  @Test
  @DisplayName("빈 댓글 내용으로 수정을 시도하면 실패한다")
  void 빈_댓글_내용_수정_실패() throws Exception {
    mockMvc.perform(patch("/api/comments/{commentId}", UUID.randomUUID())
            .header("Monew-Request-User-ID", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\" \"}"))
        .andExpect(status().isBadRequest());

    then(commentService).shouldHaveNoInteractions();
  }


  @Test
  @DisplayName("댓글 논리 삭제 성공 - RED")
  void 댓글_논리_삭제_성공() throws Exception {
    // given
    UUID commentId = UUID.randomUUID();
    mockMvc.perform(delete("/api/comments/{commentId}", commentId))
        .andExpect(status().isNoContent());

    // then
    then(commentService).should(times(1)).softDelete(commentId);
  }

  @Test
  @DisplayName("존재하지 않는 댓글을 논리 삭제하면 404 NOT FOUND를 반환한다")
  void 존재하지_않는_댓글_논리_삭제_실패() throws Exception {
    // given
    UUID commentId = UUID.randomUUID();

    willThrow(new CommentNotFoundException(commentId)).given(commentService).softDelete(commentId);

    // when & then
    mockMvc.perform(delete("/api/comments/{commentId}", commentId))
        .andExpect(status().isNotFound());

    then(commentService).should(times(1)).softDelete(commentId);
  }

  @Test
  @DisplayName("댓글 물리 삭제 성공 - GREEN")
  void 댓글_물리_삭제_성공() throws Exception {
    // given
    UUID commentId = UUID.randomUUID();
    mockMvc.perform(delete("/api/comments/{commentId}/hard", commentId))
        .andExpect(status().isNoContent())
        .andExpect(content().string("")); // 응답 비어 있는지 확인

    // then
    then(commentService).should(times(1)).hardDelete(commentId);
  }

  @Test
  @DisplayName("존재하지 않는 댓글을 물리 삭제하면 404 NOT FOUND를 반환한다")
  void 존재하지_않는_댓글_물리_삭제_실패() throws Exception {
    // given
    UUID commentId = UUID.randomUUID();

    willThrow(new CommentNotFoundException(commentId)).given(commentService).hardDelete(commentId);

    // when & then
    mockMvc.perform(delete("/api/comments/{commentId}/hard", commentId))
        .andExpect(status().isNotFound());

    then(commentService).should(times(1)).hardDelete(commentId);
  }
}

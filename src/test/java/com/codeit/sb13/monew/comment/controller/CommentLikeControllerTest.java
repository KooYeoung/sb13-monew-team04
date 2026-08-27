package com.codeit.sb13.monew.comment.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.service.CommentLikeService;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;
import com.codeit.sb13.monew.global.exception.comment.CommentNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommentLikeController.class)
@DisplayName("댓글 좋아요 컨트롤러 - TDD")
public class CommentLikeControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  private CommentLikeService commentLikeService;

  @Test
  @DisplayName("댓글 좋아요 등록 성공 - GREEN")
  void 댓글_좋아요_등록() throws Exception {
    // given
    Article article = Article.create("기사 제목", "기사 요약", "https://test.com/article",
        LocalDateTime.now(), ArticleSource.NAVER);
    User commentUser = User.builder()
        .email("comment@test.com")
        .nickname("댓글 작성자")
        .password("Abcd!")
        .build();
    User likedBy = User.builder()
        .email("like@test.com")
        .nickname("좋아요한 사용자")
        .password("Abcd!")
        .build();
    Comment comment=Comment.builder()
        .article(article)
        .user(commentUser)
        .content("테스트 댓글")
        .build();

    ReflectionTestUtils.setField(likedBy, "id", UUID.randomUUID()); // 좋아요 요청한 사용자 객체에 id 필드 설정
    ReflectionTestUtils.setField(commentUser, "id", UUID.randomUUID()); // 댓글 작성자 객체에 id 필드 설정
    ReflectionTestUtils.setField(article, "id", UUID.randomUUID()); // 기사 객체에
    ReflectionTestUtils.setField(comment, "id", UUID.randomUUID()); // 댓글 객체에 id 필드 설정

    LocalDateTime commentCreatedAt = LocalDateTime.of(2026, 6, 1, 12, 3, 5, 367_000_000); // 댓글 생성 시간 설정
    ReflectionTestUtils.setField(comment, "createdAt", commentCreatedAt); // 댓글 객체에 createdAt 필드 설정

    CommentLikeDto response=new CommentLikeDto(
        UUID.randomUUID(),
        likedBy.getId(),
        LocalDateTime.now(),
        comment.getId(),
        article.getId(),
        comment.getUser().getId(),
        comment.getUser().getNickname(),
        comment.getContent(),
        1L,
        commentCreatedAt);

    given(commentLikeService.likeComment(argThat(command->command != null
        && comment.getId().equals(command.commentId())
        && likedBy.getId().equals(command.requestUserId())))).willReturn(response);

    mockMvc.perform(
            post("/api/comments/{commentId}/comment-likes", comment.getId())
                .header("Monew-Request-User-ID", likedBy.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(response.id().toString()))
        .andExpect(jsonPath("$.likedBy").value(likedBy.getId().toString()))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.commentId").value(comment.getId().toString()))
        .andExpect(jsonPath("$.articleId").value(article.getId().toString()))
        .andExpect(jsonPath("$.commentUserId").value(commentUser.getId().toString()))
        .andExpect(jsonPath("$.commentUserNickname").value(commentUser.getNickname()))
        .andExpect(jsonPath("$.commentContent").value(comment.getContent()))
        .andExpect(jsonPath("$.commentLikeCount").value(response.commentLikeCount()))
        .andExpect(jsonPath("$.commentCreatedAt").value(commentCreatedAt.toString()));

    then(commentLikeService).should(times(1))
        .likeComment(argThat(command->
            comment.getId().equals(command.commentId())
                && likedBy.getId().equals(command.requestUserId())));
  }


  @Test
  @DisplayName("댓글 좋아요 취소 성공 - GREEN")
  void 댓글_좋아요_취소() throws Exception {
    // given
    UUID commentId = UUID.randomUUID();
    UUID requestUserId = UUID.randomUUID();

    willDoNothing()
        .given(commentLikeService)
        .unlikeComment(argThat(command -> command != null
            && commentId.equals(command.commentId())
            && requestUserId.equals(command.requestUserId())));
    // when
    mockMvc.perform(
        delete("/api/comments/{commentId}/comment-likes", commentId)
            .header("Monew-Request-User-ID", requestUserId.toString())
    ).andExpect(status().isOk());


    // then
    then(commentLikeService).should(times(1))
        .unlikeComment(argThat(command ->commentId.equals(command.commentId())
            && requestUserId.equals(command.requestUserId())));
  }

  @Test
  @DisplayName("존재하지 않는 댓글에 좋아요를 등록하면 404 오류 응답을 반환한다")
  void 댓글_좋아요_등록_댓글_없음() throws Exception {
    UUID commentId = UUID.randomUUID();
    given(commentLikeService.likeComment(argThat(command -> commentId.equals(command.commentId()))))
        .willThrow(new CommentNotFoundException(commentId));

    mockMvc.perform(post("/api/comments/{commentId}/comment-likes", commentId)
            .header("Monew-Request-User-ID", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CMT_001"));
  }
}

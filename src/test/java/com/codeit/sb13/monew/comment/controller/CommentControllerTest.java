package com.codeit.sb13.monew.comment.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.user.domain.User;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommentController.class)
@DisplayName("댓글 컨트롤러 - TDD")
public class CommentControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  private CommentService commentService;

  @Test
  @DisplayName("댓글 생성 성공 - GREEN")
  void 댓글_생성_성공() throws Exception {
    // given
    UUID articleId = UUID.randomUUID();
    User user = User.builder()
        .email("test@test.com")
        .nickname("사용자 닉네임")
        .password("Abcd!")
        .build();
    String content = "테스트 댓글";
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID()); // user 객체에 id 필드 설정

    CommentDto response=new CommentDto(UUID.randomUUID(), articleId, user.getId(), user.getNickname(), content, 0L, false, LocalDateTime.now());
    given(commentService.create(argThat(command->command != null
        && articleId.equals(command.articleId())
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
                """.formatted(articleId, user.getId(), content)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(response.id().toString()))
        .andExpect(jsonPath("$.articleId").value(articleId.toString()))
        .andExpect(jsonPath("$.userId").value(user.getId().toString()))
        .andExpect(jsonPath("$.content").value(content))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.userNickname").value("사용자 닉네임"))
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.likedByMe").value(false));
  }
}

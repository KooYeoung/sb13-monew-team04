package com.codeit.sb13.monew.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.monew.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("댓글 엔티티 - TDD")
public class CommentTest {

  @Test
  @DisplayName("댓글 생성 성공 - GREEN")
  void 댓글_생성_성공() {
    // given
    UUID articleId = UUID.randomUUID();
    User user = User.builder()
        .email("test@test.com")
        .nickname("테스트 사용자")
        .password("Abcd!")
        .build();

    // when
    Comment comment=new Comment(articleId, user, "테스트 댓글");

    // then
    Assertions.assertAll(
        ()->assertThat(comment.getArticleId()).isEqualTo(articleId),
        ()->assertThat(comment.getUser()).isEqualTo(user),
        ()->assertThat(comment.getUser().getEmail()).isEqualTo("test@test.com"),
        ()->assertThat(comment.getUser().getNickname()).isEqualTo("테스트 사용자"),
        ()->assertThat(comment.getUser().getPassword()).isEqualTo("Abcd!"),
        ()->assertThat(comment.getContent()).isEqualTo("테스트 댓글")
    );
  }
}

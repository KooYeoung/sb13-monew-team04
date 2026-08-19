package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.comment.service.impl.CommentServiceImpl;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("댓글 서비스 - TDD")
@ExtendWith(MockitoExtension.class)
public class CommentServiceTest {

  @Mock
  CommentRepository commentRepository;

  @Mock
  UserRepository userRepository;

  @InjectMocks
  private CommentServiceImpl commentService;

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
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    UUID commentId = UUID.randomUUID();

    CommentRegisterCommand command=new CommentRegisterCommand(articleId, user.getId(), "테스트 댓글");
    given(userRepository.findById(user.getId())).willReturn(java.util.Optional.of(user));
    given(commentRepository.save(any(Comment.class))).willAnswer(invocation -> {
      Comment comment = invocation.getArgument(0);
      ReflectionTestUtils.setField(comment, "id", commentId);
      return comment;
    });
    // when

    CommentDto result = commentService.create(command);
    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    // then
    then(commentRepository).should(times(1)).save(captor.capture());
    Comment savedComment = captor.getValue();
    Assertions.assertAll(
        () -> assertThat(savedComment.getArticleId()).isEqualTo(articleId),
        () -> assertThat(savedComment.getUser().getId()).isEqualTo(user.getId()),
        () -> assertThat(savedComment.getContent()).isEqualTo("테스트 댓글"),
        () -> assertThat(result.id()).isEqualTo(commentId),
        ()->assertThat(result.userId()).isEqualTo(user.getId()),
        ()->assertThat(result.articleId()).isEqualTo(articleId),
        ()->assertThat(result.content()).isEqualTo("테스트 댓글")
    );
  }
}

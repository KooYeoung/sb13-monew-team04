package com.codeit.sb13.monew.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeSaveService;
import com.codeit.sb13.monew.activity.outbox.service.OutboxEventWriter;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.CommentLikeOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CountOutboxPayload;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("댓글 좋아요 저장 서비스 테스트")
class CommentLikeSaveServiceTest {

  @Mock
  private EntityManager entityManager;

  @Mock
  private CommentLikeRepository commentLikeRepository;

  @Mock
  private OutboxEventWriter outboxEventWriter;

  @InjectMocks
  private CommentLikeSaveService commentLikeSaveService;

  @Test
  @DisplayName("댓글과 댓글 좋아요 요청자를 참조해 좋아요를 즉시 저장한다")
  void 댓글_좋아요를_즉시_저장한다() {
    // given
    UUID commentId = UUID.randomUUID();
    UUID articleId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Comment comment = org.mockito.Mockito.mock(Comment.class);
    User user = org.mockito.Mockito.mock(User.class);

    given(entityManager.getReference(Comment.class, commentId)).willReturn(comment);
    given(entityManager.getReference(User.class, userId)).willReturn(user);

    // when
    commentLikeSaveService.create(commentId, articleId, userId);

    // then
    ArgumentCaptor<CommentLike> captor = ArgumentCaptor.forClass(CommentLike.class);
    then(commentLikeRepository).should().saveAndFlush(captor.capture());
    assertThat(captor.getValue().getComment()).isEqualTo(comment);
    assertThat(captor.getValue().getLikedBy()).isEqualTo(user);
    then(outboxEventWriter).should().write(
        OutboxEventType.COMMENT_LIKED,
        OutboxAggregateType.COMMENT,
        commentId,
        userId,
        new CommentLikeOutboxPayload(articleId, OutboxEventAction.LIKED)
    );
    then(outboxEventWriter).should().write(
        OutboxEventType.COMMENT_LIKE_CHANGED,
        OutboxAggregateType.COMMENT,
        commentId,
        userId,
        new CountOutboxPayload(OutboxEventAction.COUNT_CHANGED)
    );
  }
}

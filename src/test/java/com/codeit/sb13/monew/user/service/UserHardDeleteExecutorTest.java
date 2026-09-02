package com.codeit.sb13.monew.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.activity.outbox.service.OutboxEventWriter;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UserHardDeleteExecutorTest {

  @Mock
  private CommentLikeRepository commentLikeRepository;
  @Mock
  private CommentRepository commentRepository;
  @Mock
  private ArticleViewRepository articleViewRepository;
  @Mock
  private SubscribeRepository subscribeRepository;
  @Mock
  private NotificationRepository notificationRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private OutboxEventWriter outboxEventWriter;

  @InjectMocks
  UserHardDeleteExecutor userHardDeleteExecutor;


  @Test
  @DisplayName("존재하는_userId로_물리삭제_요청_시에_정상작동")
  void 존재하는_userId로_물리삭제_요청_시에_정상작() {
    // given
    UUID userId = UUID.randomUUID();
    User user = User.builder()
        .email("email@email.com")
        .nickname("닉네임")
        .password("PassWord")
        .build();
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(user));

    // when
    userHardDeleteExecutor.hardDeleteUser(userId);

    // then
    verify(commentLikeRepository).deleteByComment_User_Id(userId);
    verify(commentLikeRepository).deleteByLikedBy_Id(userId);
    verify(commentRepository).deleteByUser_Id(userId);
    verify(articleViewRepository).deleteByUser_Id(userId);
    verify(subscribeRepository).deleteByUserId(userId);
    verify(notificationRepository).deleteByUser_Id(userId);
    verify(userRepository).deleteById(userId);
  }

  @Test
  @DisplayName("사용자 물리삭제 전에 영향 ID를 snapshot하고 삭제 후 Outbox payload로 보존한다")
  void hardDeleteSnapshotsImpactedIdsBeforeDeletion() {
    UUID userId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    UUID articleId = UUID.randomUUID();
    UUID likedCommentId = UUID.randomUUID();
    UUID viewedArticleId = UUID.randomUUID();
    UUID interestId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
        .email("payload@test.com")
        .nickname("payload")
        .password("password")
        .build()));
    when(commentRepository.findIdsByUserId(userId))
        .thenReturn(List.of(commentId, commentId));
    when(commentRepository.findArticleIdsByUserId(userId))
        .thenReturn(List.of(articleId));
    when(commentLikeRepository.findCommentIdsLikedByUserId(userId))
        .thenReturn(List.of(likedCommentId));
    when(articleViewRepository.findArticleIdsByUserId(userId))
        .thenReturn(List.of(viewedArticleId));
    when(subscribeRepository.findInterestIdsByUserId(userId))
        .thenReturn(List.of(interestId));

    userHardDeleteExecutor.hardDeleteUser(userId);

    ArgumentCaptor<UserHardDeleteOutboxPayload> payloadCaptor =
        ArgumentCaptor.forClass(UserHardDeleteOutboxPayload.class);
    verify(outboxEventWriter).write(
        eq(OutboxEventType.USER_HARD_DELETED),
        eq(OutboxAggregateType.USER),
        eq(userId),
        isNull(),
        payloadCaptor.capture()
    );
    UserHardDeleteOutboxPayload payload = payloadCaptor.getValue();
    org.assertj.core.api.Assertions.assertThat(payload.action())
        .isEqualTo(OutboxEventAction.HARD_DELETED);
    org.assertj.core.api.Assertions.assertThat(payload.authoredCommentIds())
        .containsExactly(commentId);
    org.assertj.core.api.Assertions.assertThat(payload.impactedArticleIds())
        .containsExactly(articleId);
    org.assertj.core.api.Assertions.assertThat(payload.likedCommentIds())
        .containsExactly(likedCommentId);
    org.assertj.core.api.Assertions.assertThat(payload.viewedArticleIds())
        .containsExactly(viewedArticleId);
    org.assertj.core.api.Assertions.assertThat(payload.subscribedInterestIds())
        .containsExactly(interestId);
    verify(userRepository).deleteById(userId);
  }

  @Test
  @DisplayName("존재하지 않는 userId로 물리 삭제 요청시 예외를 터트린다.")
  void 존재하지_않는_userId로_물리삭제_요청_시_예외를_던진다() {
    // given
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> userHardDeleteExecutor.hardDeleteUser(userId))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("존재하지 않는 userId로 만료 회원 물리 삭제 요청시 예외를 터트린다.")
  void 존재하지_않는_userId로_만료회원_물리삭제_요청_시_예외를_던진다() {
    // given
    UUID uuid = UUID.randomUUID();
    LocalDateTime threshold = LocalDateTime.now();
    when(userRepository.findById(uuid))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> userHardDeleteExecutor.hardDeleteExpiredUser(uuid, threshold))
        .isInstanceOf(UserNotFoundException.class);

  }

  @Test
  @DisplayName("threshold보다 이전에 삭제됐다면 물리 삭제된다.")
  void threshold보다_이전에_삭제됐다면_물리삭제() {
    // given
    UUID uuid = UUID.randomUUID();
    User user = User.builder()
        .email("email@email.com")
        .nickname("닉네임")
        .password("PassWord123!")
        .build();
    user.softDelete();
    LocalDateTime threshold = LocalDateTime.now().plusDays(1);
    when(userRepository.findById(uuid))
        .thenReturn(Optional.of(user));

    // when
    userHardDeleteExecutor.hardDeleteExpiredUser(uuid, threshold);

    // then
    verify(userRepository).deleteById(uuid);

  }

  @Test
  @DisplayName("threshold보다 이후에 삭제됐다면 물리 삭제되지 않는다.")
  void threshold보다_이후에_삭제됐다면_물리_삭제되지_않는다() {
    // given
    UUID uuid = UUID.randomUUID();
    User user = User.builder()
        .email("email@email.com")
        .nickname("닉네임")
        .password("PassWord123!")
        .build();
    user.softDelete();
    LocalDateTime threshold = LocalDateTime.now().minusDays(1);
    when(userRepository.findById(uuid))
        .thenReturn(Optional.of(user));

    // when
    userHardDeleteExecutor.hardDeleteExpiredUser(uuid, threshold);

    // then
    verify(userRepository, never()).deleteById(uuid);
  }

  @Test
  @DisplayName("사용자가 복구되어 deletedAt이 null이면 물리 삭제되지 않는다.")
  void deletedAt이_null이면_물리_삭제되지_않는다() {
    // given
    UUID uuid = UUID.randomUUID();
    User user = User.builder()
        .email("email@email.com")
        .nickname("닉네임")
        .password("PassWord123!")
        .build();
    LocalDateTime threshold = LocalDateTime.now().minusDays(1);
    //softDelete를 호출하지 않아서 deletedAt은 null이다.
    when(userRepository.findById(uuid))
        .thenReturn(Optional.of(user));

    // when
    userHardDeleteExecutor.hardDeleteExpiredUser(uuid, threshold);

    // then
    verify(userRepository, never()).deleteById(uuid);
  }

}

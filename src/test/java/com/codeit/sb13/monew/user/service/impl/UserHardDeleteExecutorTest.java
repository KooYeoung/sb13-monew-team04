package com.codeit.sb13.monew.user.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import com.codeit.sb13.monew.user.service.UserHardDeleteExecutor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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



}

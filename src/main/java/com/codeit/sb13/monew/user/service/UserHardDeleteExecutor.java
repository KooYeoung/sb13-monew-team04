package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.interest.repository.SubscribeRepository;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserHardDeleteExecutor {
  private final CommentLikeRepository commentLikeRepository;
  private final CommentRepository commentRepository;
  private final ArticleViewRepository articleViewRepository;
  private final SubscribeRepository subscribeRepository;
  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void hardDeleteUser(UUID userId) {
    userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
    deleteAllRelateDataData(userId);
  }

  private void deleteAllRelateDataData(UUID userId) {
    // FK 제약 순서 고려
    // CommentLike→ Comment → ArticleView → Subscribe → Notification → User
    commentLikeRepository.deleteByComment_User_Id(userId);
    commentLikeRepository.deleteByLikedBy_Id(userId);
    commentRepository.deleteByUser_Id(userId);
    articleViewRepository.deleteByUser_Id(userId);
    subscribeRepository.deleteByUserId(userId);
    notificationRepository.deleteByUser_Id(userId);
    userRepository.deleteById(userId);

  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void hardDeleteExpiredUser(UUID userId, LocalDateTime threshold) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
    LocalDateTime deletedAt = user.getDeletedAt();
    if(deletedAt != null &&  deletedAt.isBefore(threshold)) {
      deleteAllRelateDataData(userId);
    }
  }

}

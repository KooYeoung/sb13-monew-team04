package com.codeit.sb13.monew.user.service;

import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.activity.outbox.payload.UserHardDeleteOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.service.OutboxEventWriter;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserHardDeleteExecutor {
  private final CommentLikeRepository commentLikeRepository;
  private final CommentRepository commentRepository;
  private final ArticleViewRepository articleViewRepository;
  private final SubscribeRepository subscribeRepository;
  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final OutboxEventWriter outboxEventWriter;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void hardDeleteUser(UUID userId) {
    log.debug("물리 삭제 요청 - userId: {}", userId);

    userRepository.findById(userId)
        .orElseThrow(() ->  new UserNotFoundException(userId));

    UserHardDeleteOutboxPayload payload = capturePayload(userId);
    deleteAllRelatedData(userId);
    writeHardDeleteEvent(userId, payload);
    log.info("물리 삭제 성공 - userId: {}", userId);
  }

  private void deleteAllRelatedData(UUID userId) {
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

  private UserHardDeleteOutboxPayload capturePayload(UUID userId) {
    return new UserHardDeleteOutboxPayload(
        OutboxEventAction.HARD_DELETED,
        commentRepository.findIdsByUserId(userId),
        commentRepository.findArticleIdsByUserId(userId),
        commentLikeRepository.findCommentIdsLikedByUserId(userId),
        articleViewRepository.findArticleIdsByUserId(userId),
        subscribeRepository.findInterestIdsByUserId(userId)
    );
  }

  private void writeHardDeleteEvent(UUID userId, UserHardDeleteOutboxPayload payload) {
    outboxEventWriter.write(
        OutboxEventType.USER_HARD_DELETED,
        OutboxAggregateType.USER,
        userId,
        null,
        payload
    );
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void hardDeleteExpiredUser(UUID userId, LocalDateTime threshold) {
    log.debug("논리 삭제 후 하루 경과 검토 - userId: {}", userId);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
    LocalDateTime deletedAt = user.getDeletedAt();
    if(deletedAt != null &&  deletedAt.isBefore(threshold)) {
      UserHardDeleteOutboxPayload payload = capturePayload(userId);
      deleteAllRelatedData(userId);
      writeHardDeleteEvent(userId, payload);
      log.info("물리 삭제 성공 - userId: {}", userId);
    }else {
      log.info("물리 삭제 스킵 - 삭제 조건 미충족(복구되었거나 유예기간 이내) - userId: {}, deletedAt: {}",
          userId, deletedAt);
    }
  }

}

package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.activity.outbox.payload.CommentLikeOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CountOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.service.OutboxEventWriter;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentLikeSaveService {
  private final EntityManager entityManager;
  private final CommentLikeRepository commentLikeRepository;
  private final OutboxEventWriter outboxEventWriter;

  // UNIQUE 제약 조건 위반 시 실패하는 INSERT 트랜잭션만 롤백되도록 REQUIRES_NEW 로 분리
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void create(UUID commentId, UUID articleId, UUID userId) {

    commentLikeRepository.saveAndFlush(CommentLike.builder()
        .comment(entityManager.getReference(Comment.class, commentId))
        .likedBy(entityManager.getReference(User.class, userId))
        .build());
    outboxEventWriter.write(
        OutboxEventType.COMMENT_LIKED,
        OutboxAggregateType.COMMENT,
        commentId,
        userId,
        new CommentLikeOutboxPayload(
            articleId,
            OutboxEventAction.LIKED
        )
    );
    outboxEventWriter.write(
        OutboxEventType.COMMENT_LIKE_CHANGED,
        OutboxAggregateType.COMMENT,
        commentId,
        userId,
        new CountOutboxPayload(
            OutboxEventAction.COUNT_CHANGED
        )
    );
  }
}

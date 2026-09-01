package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.CommentLikeService;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeRegisterCommand;
import com.codeit.sb13.monew.global.exception.comment.CommentLikeNotFoundException;
import com.codeit.sb13.monew.global.exception.comment.CommentNotFoundException;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Validated
@Service
@RequiredArgsConstructor
public class CommentLikeServiceImpl implements CommentLikeService {

  private final CommentLikeRepository commentLikeRepository;
  private final CommentRepository commentRepository;
  private final UserService userService;
  private final CommentLikeSaveService commentLikeSaveService;
  private final NotificationService notificationService;

  // 기존 좋아요가 없을 때만 사용자 엔티티를 조회해 새로운 좋아요를 등록한다.
  @Override
  public CommentLikeDto likeComment(CommentLikeRegisterCommand command) {
    UUID commentId = command.commentId();
    UUID likedById = command.requestUserId();

    log.debug("댓글 좋아요 등록 시작 - 댓글 아이디: {}", commentId);

    Comment comment = commentRepository.findActiveById(commentId)
            .orElseThrow(() -> new CommentNotFoundException(commentId));
    User likedByUser = userService.findActiveById(likedById); // 활성 사용자만 댓글 등록 허용
    return commentLikeRepository.findActiveResponseProjection(commentId, likedById)
        .map(CommentLikeDto::from)
        .orElseGet(() -> createOrReturnExisting(comment, likedByUser));
  }

  // 새로운 트랜잭션으로 댓글 좋아요 등록 시도 -> 동시 중복 요청 발생으로 UNIQUE 제약을 위반하면 기존 좋아요 반환
  private CommentLikeDto createOrReturnExisting(Comment comment, User likedByUser) {
    try {
      commentLikeSaveService.create(comment.getId(), likedByUser.getId());
      log.info("댓글 좋아요 등록 완료 - 댓글 아이디: {}", comment.getId());

    } catch (DataIntegrityViolationException e) {
      if (!isDuplicateCommentLike(e)) {
        throw e;
      }
      return commentLikeRepository.findActiveResponseProjection(
              comment.getId(), likedByUser.getId())
          .map(existingLike -> {
            log.debug("댓글 좋아요 중복 감지 -> 기존 댓글 좋아요 반환 - 댓글 아이디: {}", comment.getId());
            return CommentLikeDto.from(existingLike);
          })
          .orElseThrow(() -> e);
    }

    try {
      notificationService.notifyCommentLiked(new CommentLikedDto(likedByUser, comment.getUser(), comment.getId()));
    } catch (RuntimeException e) {
      log.error("댓글 좋아요 알림 저장 실패 - 좋아요 자체는 정상 등록됨. commentId={}, senderId={}, recipientId={}",
              comment.getId(), likedByUser.getId(), comment.getUser().getId(), e);
    }

    return commentLikeRepository.findActiveResponseProjection(
            comment.getId(), likedByUser.getId())
        .map(CommentLikeDto::from)
        .orElseThrow(()->new IllegalStateException("댓글 좋아요 등록 후 조회 실패 - 댓글 아이디: " + comment.getId()));
  }

  // UNIQUE 제약 조건 위반일 때만 중복 좋아요로 판단하고, 다른 제약 조건 위반 시 해당 예외를 그대로 던진다
  private boolean isDuplicateCommentLike(DataIntegrityViolationException e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolation
          && "uk_comment_likes_comment_liked_by".equals(
              constraintViolation.getConstraintName())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  @Override
  @Transactional
  public void unlikeComment(CommentLikeRegisterCommand command) {
    UUID commentId = command.commentId();
    UUID requestUserId = command.requestUserId();

    log.debug("댓글 좋아요 취소 시작 - 댓글 아이디: {}", commentId);

    commentRepository.findActiveById(commentId).orElseThrow(()-> new CommentNotFoundException(commentId));
    userService.findActiveById(requestUserId); // 활성화된 사용자만 댓글 취소 가능

    Long deletedCount = commentLikeRepository.deleteByCommentIdAndLikedById(commentId, requestUserId);
    if (deletedCount == 0L) {
      throw new CommentLikeNotFoundException(commentId, requestUserId);
    }

    log.info("댓글 좋아요 취소 완료 - 댓글 아이디: {}", commentId);
  }
}

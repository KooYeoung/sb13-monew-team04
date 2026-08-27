package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.CommentLikeService;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeRegisterCommand;
import com.codeit.sb13.monew.global.exception.comment.CommentLikeNotFoundException;
import com.codeit.sb13.monew.global.exception.comment.CommentNotFoundException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
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
  private final UserRepository userRepository;
  private final CommentLikeSaveService commentLikeSaveService;
  private final NotificationService notificationService;

  // 댓글, 사용자 존재 여부 확인 -> 기존 좋아요가 없다면 새로운 좋아요 등록, 이미 좋아요가 있다면 기존 좋아요 반환
  @Override
  public CommentLikeDto likeComment(CommentLikeRegisterCommand command) {
    UUID commentId = command.commentId();
    UUID likedById = command.requestUserId();

    log.debug("댓글 좋아요 등록 시작 - 댓글 아이디: {}", commentId);

    Comment comment = commentRepository.findActiveById(commentId)
            .orElseThrow(() -> new CommentNotFoundException(commentId));
    User likedByUser = userRepository.findByIdAndDeletedAtIsNull(likedById)
            .orElseThrow(() -> new UserNotFoundException(likedById));

    return commentLikeRepository.findWithCommentDetailsByCommentIdAndLikedById(commentId, likedById)
        .map(this::toDto)
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
      return commentLikeRepository.findWithCommentDetailsByCommentIdAndLikedById(
              comment.getId(), likedByUser.getId())
          .map(existingLike -> {
            log.debug("댓글 좋아요 중복 감지 -> 기존 댓글 좋아요 반환 - 댓글 아이디: {}", comment.getId());
            return toDto(existingLike);
          })
          .orElseThrow(() -> e);
    }

    try {
      notificationService.notifyCommentLiked(new CommentLikedDto(likedByUser, comment.getUser(), comment.getId()));
    } catch (RuntimeException e) {
      log.error("댓글 좋아요 알림 저장 실패 - 좋아요 자체는 정상 등록됨. commentId={}, senderId={}, recipientId={}",
              comment.getId(), likedByUser.getId(), comment.getUser().getId(), e);
    }

    return commentLikeRepository.findWithCommentDetailsByCommentIdAndLikedById(
            comment.getId(), likedByUser.getId())
        .map(this::toDto)
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
    userRepository.findByIdAndDeletedAtIsNull(requestUserId)
        .orElseThrow(()->new UserNotFoundException(requestUserId));

    Long deletedCount = commentLikeRepository.deleteByCommentIdAndLikedById(commentId, requestUserId);
    if (deletedCount == 0L) {
      throw new CommentLikeNotFoundException(commentId, requestUserId);
    }

    log.info("댓글 좋아요 취소 완료 - 댓글 아이디: {}", commentId);
  }

  // 댓글 좋아요가 성공적으로 등록되면 재조회하여 좋아요 수를 포함한 DTO 반환
  private CommentLikeDto toDto(CommentLike commentLike) {
    Long likeCount = commentLikeRepository.countActiveLikesByCommentId(commentLike.getComment().getId());
    return CommentLikeDto.from(commentLike, likeCount);
  }
}

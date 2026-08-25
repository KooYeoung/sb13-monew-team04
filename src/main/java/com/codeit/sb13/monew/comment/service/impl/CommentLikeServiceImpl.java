package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.comment.domain.CommentLike;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.CommentLikeService;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeDto;
import com.codeit.sb13.monew.comment.service.dto.CommentLikeRegisterCommand;
import com.codeit.sb13.monew.global.exception.comment.CommentLikeNotFoundException;
import com.codeit.sb13.monew.global.exception.comment.CommentNotFoundException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  // 댓글, 사용자 존재 여부 확인 -> 기존 좋아요가 없다면 새로운 좋아요 등록, 이미 좋아요가 있다면 기존 좋아요 반환
  @Override
  public CommentLikeDto likeComment(CommentLikeRegisterCommand command) {
    UUID commentId = command.commentId();
    UUID likedById = command.requestUserId();

    log.debug("댓글 좋아요 등록 시작 - 댓글 아이디: {}", commentId);

    commentRepository.findByIdAndDeletedAtIsNull(commentId).orElseThrow(()-> new CommentNotFoundException(commentId));
    userRepository.findById(likedById).orElseThrow(()->new UserNotFoundException(likedById));

    return commentLikeRepository.findByCommentAndLikedBy(commentId, likedById)
        .map(this::toDto)
        .orElseGet(() -> createOrReturnExisting(commentId, likedById));
  }

  // 새로운 트랜잭션으로 댓글 좋아요 등록 시도 -> 동시 중복 요청 발생으로 UNIQUE 제약을 위반하면 기존 좋아요 반환
  private CommentLikeDto createOrReturnExisting(UUID commentId, UUID likedById) {
    try {
      commentLikeSaveService.create(commentId, likedById);
      log.info("댓글 좋아요 등록 완료 - 댓글 아이디: {}", commentId);

    } catch (DataIntegrityViolationException e) {
      return commentLikeRepository.findByCommentAndLikedBy(commentId, likedById)
          .map(existingLike -> {
            log.debug("댓글 좋아요 중복 감지 -> 기존 댓글 좋아요 반환 - 댓글 아이디: {}", commentId);
            return toDto(existingLike);
          })
          .orElseThrow(() -> e);
    }

    return commentLikeRepository.findByCommentAndLikedBy(commentId, likedById)
        .map(this::toDto)
        .orElseThrow(()->new IllegalStateException("댓글 좋아요 등록 후 조회 실패 - 댓글 아이디: " + commentId));
  }

  @Override
  @Transactional
  public void unlikeComment(CommentLikeRegisterCommand command) {
    UUID commentId = command.commentId();
    UUID requestUserId = command.requestUserId();

    log.debug("댓글 좋아요 취소 시작 - 댓글 아이디: {}", commentId);

    commentRepository.findByIdAndDeletedAtIsNull(commentId).orElseThrow(()-> new CommentNotFoundException(commentId));
    userRepository.findById(requestUserId).orElseThrow(()->new UserNotFoundException(requestUserId));

    Long deletedCount = commentLikeRepository.deleteByCommentIdAndLikedById(commentId, requestUserId);
    if (deletedCount == 0L) {
      throw new CommentLikeNotFoundException(commentId, requestUserId);
    }

    log.info("댓글 좋아요 취소 완료 - 댓글 아이디: {}", commentId);
  }

  // 댓글 좋아요가 성공적으로 등록되면 재조회하여 좋아요 수를 포함한 DTO 반환
  private CommentLikeDto toDto(CommentLike commentLike) {
    Long likeCount = commentLikeRepository.countByCommentId(commentLike.getComment().getId());
    return CommentLikeDto.from(commentLike, likeCount);
  }
}

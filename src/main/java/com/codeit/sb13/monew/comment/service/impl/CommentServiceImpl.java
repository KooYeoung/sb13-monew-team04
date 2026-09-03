package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.activity.service.ActivityVisibilityUpdater;
import com.codeit.sb13.monew.activity.outbox.payload.CommentOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.payload.CountOutboxPayload;
import com.codeit.sb13.monew.activity.outbox.service.OutboxEventWriter;
import com.codeit.sb13.monew.activity.outbox.service.OutboxProjectionImpactReader;
import com.codeit.sb13.monew.activity.outbox.payload.ProjectionImpact;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxAggregateType;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventAction;
import com.codeit.sb13.monew.activity.outbox.domain.OutboxEventType;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentLikeRepository;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CursorPageResponseCommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.comment.service.dto.CommentSearchCommand;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchCondition;
import com.codeit.sb13.monew.comment.repository.dto.CommentSearchResult;
import com.codeit.sb13.monew.comment.service.dto.CommentUpdateCommand;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.comment.CommentNotFoundException;
import com.codeit.sb13.monew.global.exception.comment.CommentPermissionDeniedException;
import com.codeit.sb13.monew.global.domain.ActivityVisibilityStatus;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Validated // 서비스 계층에서도 Bean validation 적용하기 위해 추가
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

  private final CommentRepository commentRepository;
  private final UserService userService;
  private final ArticleRepository articleRepository;
  private final CommentLikeRepository commentLikeRepository;
  private final ActivityVisibilityUpdater activityVisibilityUpdater;
  private final OutboxEventWriter outboxEventWriter;
  private final OutboxProjectionImpactReader projectionImpactReader;

  @Transactional
  @Override
  public CommentDto create(CommentRegisterCommand command) { // 서비스 전용객체를 사용
    log.debug("댓글 생성 시작 - 기사 아이디: {}", command.articleId()); // 개인 정보 또는 민감한 정보는 로그에 남기지 않음
    User user = userService.findActiveById(command.userId()); // 활성화된 사용자만 조회(논리 삭제된 사용자 댓글 생성 방지)
    Article article = articleRepository.findByIdAndDeletedAtIsNull(command.articleId())
        .orElseThrow(()->new ArticleNotFoundException(command.articleId()));
    Comment comment= Comment.builder()
        .article(article).user(user).content(command.content())
        .build();
    Comment savedComment = commentRepository.save(comment);
    outboxEventWriter.write(
        OutboxEventType.COMMENT_WRITTEN,
        OutboxAggregateType.COMMENT,
        savedComment.getId(),
        command.userId(),
        new CommentOutboxPayload(
            savedComment.getArticle().getId(),
            OutboxEventAction.WRITTEN
        )
    );
    writeArticleCommentCountChanged(savedComment.getArticle().getId(), command.userId());
    log.info("댓글 생성 완료 - 댓글 아이디: {}, 기사 아이디: {}", savedComment.getId(), savedComment.getArticle().getId());
    return CommentDto.from(savedComment, 0L, false); // 댓글 생성 직후, 좋아요 수는 0, 좋아요 여부는 false로 반환
  }

  @Override
  public CursorPageResponseCommentDto search(CommentSearchCommand command) { // Swagger API 응답과 맞춘다
    CommentSearchResult page=commentRepository.search(new CommentSearchCondition(
        command.articleId(),
        command.orderBy(),
        command.direction(),
        command.cursor(),
        command.after(),
        command.limit(),
        command.requestUserId()
    ));

    List<CommentDto> content = page.rows().stream()
        .map(CommentDto::from)
        .toList();

    return new CursorPageResponseCommentDto(
        content,
        nextCursor(content),
        nextAfter(content),
        content.size(),
        page.totalElements(),
        page.hasNext()
    );
  }

  // 다음 페이지 cursor는 마지막 댓글 ID
  private String nextCursor(List<CommentDto> content) {
    if (content.isEmpty()) {
      return null;
    }

    return content.get(content.size() - 1).id().toString();
  }

  // 다음 페이지 조회에 사용할 보조 커서
  private String nextAfter(List<CommentDto> content) {
    if (content.isEmpty()) {
      return null;
    }

    return content.get(content.size() - 1).createdAt().toString();
  }

  @Override
  public Comment findActiveById(UUID commentId) {
    return commentRepository.findActiveById(commentId)
        .orElseThrow(() -> new CommentNotFoundException(commentId));
  }

  @Transactional
  @Override
  public CommentDto update(CommentUpdateCommand command) {
    Comment comment = findActiveById(command.commentId());

    if (!comment.getUser().getId().equals(command.requestUserId())) {
      throw new CommentPermissionDeniedException(command.commentId(), command.requestUserId());
    }

    comment.changeContent(command.content());
    outboxEventWriter.write(
        OutboxEventType.COMMENT_UPDATED,
        OutboxAggregateType.COMMENT,
        comment.getId(),
        command.requestUserId(),
        new CommentOutboxPayload(
            comment.getArticle().getId(),
            OutboxEventAction.UPDATED
        )
    );
    Long likeCount = commentLikeRepository.countActiveLikesByCommentId(
        command.commentId());// 좋아요 수를 업데이트하기 위해 count 조회
    boolean likedBy = commentLikeRepository.existsActiveByCommentIdAndLikedById(
        comment.getId(), command.requestUserId());// 좋아요 여부를 업데이트하기 위해 조회

    return CommentDto.from(comment, likeCount, likedBy);
  }


  @Transactional
  @Override
  public void softDelete(UUID commentId) {
    ProjectionImpact impact = projectionImpactReader.forComment(commentId);
    int updatedCount = commentRepository.softDeleteIfNotDeleted(commentId, LocalDateTime.now());
    if (updatedCount == 0) {
      // API 계약상 이미 삭제된 댓글과 존재하지 않는 댓글 모두 404로 응답한다 ("404 댓글 정보 없음")
      throw new CommentNotFoundException(commentId);
    }
    Comment comment = commentRepository.findForHardDeleteById(commentId)
        .orElseThrow(() -> new CommentNotFoundException(commentId));
    UUID articleId = comment.getArticle().getId();

    long commentLikeCount = activityVisibilityUpdater.hideActiveByDeletedComment(commentId);
    outboxEventWriter.write(
        OutboxEventType.COMMENT_SOFT_DELETED,
        OutboxAggregateType.COMMENT,
        commentId,
        null,
        new CommentOutboxPayload(
            articleId,
            OutboxEventAction.SOFT_DELETED,
            impact
        )
    );
    writeArticleCommentCountChanged(articleId, null);
    log.info("댓글 논리 삭제 성공 - commentId: {}, 숨김 처리된 댓글 좋아요 수: {}", commentId, commentLikeCount);
  }

  @Transactional
  @Override
  public void hardDelete(UUID commentId) {
    Comment comment = commentRepository.findForHardDeleteById(commentId)
        .orElseThrow(() -> new CommentNotFoundException(commentId));
    ProjectionImpact impact = projectionImpactReader.forComment(commentId);
    UUID articleId = comment.getArticle().getId();
    boolean wasActive = comment.getVisibilityStatus() == ActivityVisibilityStatus.ACTIVE;
    commentLikeRepository.deleteByCommentId(commentId);
    commentRepository.delete(comment);
    outboxEventWriter.write(
        OutboxEventType.COMMENT_HARD_DELETED,
        OutboxAggregateType.COMMENT,
        commentId,
        null,
        new CommentOutboxPayload(
            articleId,
            OutboxEventAction.HARD_DELETED,
            impact
        )
    );
    if (wasActive) {
      writeArticleCommentCountChanged(articleId, null);
    }
  }

  private void writeArticleCommentCountChanged(UUID articleId, UUID actorUserId) {
    outboxEventWriter.write(
        OutboxEventType.ARTICLE_COMMENT_COUNT_CHANGED,
        OutboxAggregateType.ARTICLE,
        articleId,
        actorUserId,
        new CountOutboxPayload(
            OutboxEventAction.COUNT_CHANGED
        )
    );
  }

  @Override
  public void validateActiveExists(UUID commentId) {
    if (!commentRepository.existsActiveById(commentId)) {
      throw new CommentNotFoundException(commentId);
    }
  }
}

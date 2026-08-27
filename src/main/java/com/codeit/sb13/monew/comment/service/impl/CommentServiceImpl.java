package com.codeit.sb13.monew.comment.service.impl;

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
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
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
  private final UserRepository userRepository;
  private final ArticleRepository articleRepository;
  private final CommentLikeRepository commentLikeRepository;
  // TODO: 추후 UserService나 사용자 조회 전용 컴포넌트에 조회 메서드를 두고 해당 서비스를 통해 사용자 정보 가져오도록 변경

  @Transactional
  @Override
  public CommentDto create(CommentRegisterCommand command) { // 서비스 전용객체를 사용
    log.debug("댓글 생성 시작 - 기사 아이디: {}", command.articleId()); // 개인 정보 또는 민감한 정보는 로그에 남기지 않음
    User user = userRepository.findByIdAndDeletedAtIsNull(command.userId())
        .orElseThrow(()->new UserNotFoundException(command.userId()));
    Article article = articleRepository.findByIdAndDeletedAtIsNull(command.articleId())
        .orElseThrow(()->new ArticleNotFoundException(command.articleId()));
    Comment comment= Comment.builder()
        .article(article).user(user).content(command.content())
        .build();
    Comment savedComment = commentRepository.save(comment);
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

  @Transactional
  @Override
  public CommentDto update(CommentUpdateCommand command) {
    Comment comment = commentRepository.findActiveById(command.commentId())
        .orElseThrow(() -> new CommentNotFoundException(command.commentId()));

    if (!comment.getUser().getId().equals(command.requestUserId())) {
      throw new CommentPermissionDeniedException(command.commentId(), command.requestUserId());
    }

    comment.changeContent(command.content());
    Long likeCount = commentLikeRepository.countActiveLikesByCommentId(
        command.commentId());// 좋아요 수를 업데이트하기 위해 count 조회
    boolean likedBy = commentLikeRepository.existsActiveByCommentIdAndLikedById(
        comment.getId(), command.requestUserId());// 좋아요 여부를 업데이트하기 위해 조회

    return CommentDto.from(comment, likeCount, likedBy);
  }


  @Transactional
  @Override
  public void softDelete(UUID commentId) {
    int updatedCount = commentRepository.softDeleteIfNotDeleted(commentId, LocalDateTime.now());
    if (updatedCount == 0) {
      // API 계약상 이미 삭제된 댓글과 존재하지 않는 댓글 모두 404로 응답한다 ("404 댓글 정보 없음")
      throw new CommentNotFoundException(commentId);
    }
  }

  @Transactional
  @Override
  public void hardDelete(UUID commentId) {
    Comment comment = commentRepository.findForHardDeleteById(commentId)
        .orElseThrow(() -> new CommentNotFoundException(commentId));
    commentLikeRepository.deleteByCommentId(commentId);
    commentRepository.delete(comment);
  }
}

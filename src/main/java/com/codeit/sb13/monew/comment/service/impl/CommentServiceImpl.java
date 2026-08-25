package com.codeit.sb13.monew.comment.service.impl;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.comment.domain.Comment;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.comment.service.CommentService;
import com.codeit.sb13.monew.comment.service.dto.CommentDto;
import com.codeit.sb13.monew.comment.service.dto.CommentRegisterCommand;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
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
  // TODO: 추후 UserService나 사용자 조회 전용 컴포넌트에 조회 메서드를 두고 해당 서비스를 통해 사용자 정보 가져오도록 변경

  @Transactional
  @Override
  public CommentDto create(CommentRegisterCommand command) { // 서비스 전용객체를 사용
    log.debug("댓글 생성 시작 - 기사 아이디: {}", command.articleId()); // 개인 정보 또는 민감한 정보는 로그에 남기지 않음
    User user = userRepository.findById(command.userId()).orElseThrow(()->new UserNotFoundException(command.userId()));
    Article article = articleRepository.findById(command.articleId()).orElseThrow(()->new ArticleNotFoundException(command.articleId()));
    Comment comment= Comment.builder()
        .article(article).user(user).content(command.content())
        .build();
    Comment savedComment = commentRepository.save(comment);
    log.info("댓글 생성 완료 - 댓글 아이디: {}, 기사 아이디: {}", savedComment.getId(), savedComment.getArticle().getId());
    return CommentDto.from(savedComment, 0L, false); // 댓글 생성 직후, 좋아요 수는 0, 좋아요 여부는 false로 반환
  }
}

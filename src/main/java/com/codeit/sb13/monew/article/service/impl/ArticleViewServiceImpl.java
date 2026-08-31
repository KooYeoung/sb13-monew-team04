package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.mapper.ArticleMapper;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import com.codeit.sb13.monew.comment.repository.CommentRepository;
import com.codeit.sb13.monew.global.exception.article.ArticleViewConflictException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleViewServiceImpl implements ArticleViewService {

    private final ArticleViewRepository articleViewRepository;
    private final ArticleService articleService;
    private final UserService userService;
    private final ArticleMapper articleMapper;
    private final ArticleViewSaveService articleViewSaveService;
    private final CommentRepository commentRepository;

    @Override
    @Transactional
    public ArticleViewDto recordView(UUID articleId, UUID userId) {
        Article article = articleService.findById(articleId);
        User user = userService.findById(userId);

        ArticleView articleView = articleViewRepository.findByArticleAndUser(article, user)
                .map(this::touch)
                .orElseGet(() -> createOrTouchExisting(article, user));

        long viewCount = articleViewRepository.countActiveByArticleId(articleId);

        long commentCount = commentRepository.countActiveByArticleId(articleId);

        return articleMapper.toViewDto(articleView, commentCount, viewCount);
    }

    // 기존 조회 기록의 조회 시각만 갱신한다.
    private ArticleView touch(ArticleView articleView) {
        articleView.updateViewedAt(LocalDateTime.now());
        return articleViewRepository.save(articleView);
    }

    /**
     * 별도 트랜잭션으로 INSERT를 시도한다. 같은 (기사, 사용자) 조합의 동시 요청으로 UNIQUE 제약을
     * 위반하면 상대 트랜잭션은 이미 커밋된 상태이므로, 그 행을 다시 조회해 조회 시각을 갱신하는
     * 방식으로 서버가 스스로 복구한다. (MID4-164)
     */
    private ArticleView createOrTouchExisting(Article article, User user) {
        try {
            articleViewSaveService.create(article.getId(), user.getId(), LocalDateTime.now());
        } catch (DataIntegrityViolationException e) {
            if (!isArticleViewUniqueViolation(e)) {
                throw e;
            }
            return articleViewRepository.findByArticleAndUser(article, user)
                    .map(this::touch)
                    .orElseThrow(ArticleViewConflictException::new);
        }
        return articleViewRepository.findByArticleAndUser(article, user)
                .orElseThrow(ArticleViewConflictException::new);
    }

    private boolean isArticleViewUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        // 제약명 표기는 DB 벤더와 설정에 따라 대소문자가 달라진다.
        return message != null
                && message.toLowerCase(Locale.ROOT).contains("uk_article_views_article_user");
    }

}

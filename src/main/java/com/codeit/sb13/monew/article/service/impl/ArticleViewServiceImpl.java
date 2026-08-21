package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.mapper.ArticleMapper;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;
import com.codeit.sb13.monew.global.exception.article.ArticleViewConflictException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleViewServiceImpl implements ArticleViewService {

    private final ArticleViewRepository articleViewRepository;
    private final ArticleService articleService;
    private final UserService userService;
    private final ArticleMapper articleMapper;

    @Override
    @Transactional
    public ArticleViewDto recordView(UUID articleId, UUID userId) {
        Article article = articleService.findById(articleId);
        User user = userService.findById(userId);

        ArticleView articleView = articleViewRepository.findByArticleAndUser(article, user)
                .map(view -> {
                    view.updateViewedAt(LocalDateTime.now());
                    return articleViewRepository.save(view);
                })
                .orElseGet(() -> saveNewView(article, user));

        long viewCount = articleViewRepository.countByArticle(article);

        // commentCount는 댓글 집계 방식 확정 전까지 0 (MID4-163 → MID4-147)
        return articleMapper.toViewDto(articleView, 0L, viewCount);
    }

    @Override
    public long getViewCount(UUID articleId) {
        Article article = articleService.findById(articleId);
        return articleViewRepository.countByArticle(article);
    }

    @Override
    public List<ArticleView> getUserArticleViews(UUID userId) {
        User user = userService.findById(userId);
        return articleViewRepository.findByUserOrderByViewedAtDesc(user);
    }

    @Override
    public List<ArticleView> getArticleViews(UUID articleId) {
        Article article = articleService.findById(articleId);
        return articleViewRepository.findByArticleOrderByViewedAtDesc(article);
    }

    private ArticleView saveNewView(Article article, User user) {
        try {
            return articleViewRepository.saveAndFlush(
                    ArticleView.create(article, user, LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            if (isArticleViewUniqueViolation(e)) {
                throw new ArticleViewConflictException();
            }
            throw e;
        }
    }

    private boolean isArticleViewUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_article_views_article_user");
    }

}
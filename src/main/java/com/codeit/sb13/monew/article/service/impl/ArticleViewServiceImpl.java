package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.ArticleViewService;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ArticleView recordView(UUID articleId, UUID userId) {
        // Article, User 조회
        Article article = articleService.findById(articleId);
        User user = getUserOrThrow(userId);

        // 기존 조회 기록 확인
        return articleViewRepository.findByArticleAndUser(article, user)
                .map(view -> {
                    view.updateViewedAt(LocalDateTime.now());
                    return articleViewRepository.save(view);
                })
                .orElseGet(() -> {
                    ArticleView newView = ArticleView.create(article, user, LocalDateTime.now());
                    return articleViewRepository.save(newView);
                });
    }

    @Override
    public long getViewCount(UUID articleId) {
        Article article = articleService.findById(articleId);
        return articleViewRepository.countByArticle(article);
    }

    @Override
    public List<ArticleView> getUserArticleViews(UUID userId) {
        User user = getUserOrThrow(userId);
        return articleViewRepository.findByUserOrderByViewedAtDesc(user);
    }

    @Override
    public List<ArticleView> getArticleViews(UUID articleId) {
        Article article = articleService.findById(articleId);
        return articleViewRepository.findByArticleOrderByViewedAtDesc(article);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
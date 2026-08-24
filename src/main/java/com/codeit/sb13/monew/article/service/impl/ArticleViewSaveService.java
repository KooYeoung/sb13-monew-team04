package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.user.domain.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleViewSaveService {

    private final EntityManager entityManager;
    private final ArticleViewRepository articleViewRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(UUID articleId, UUID userId, LocalDateTime viewedAt) {
        articleViewRepository.saveAndFlush(ArticleView.create(
                entityManager.getReference(Article.class, articleId),
                entityManager.getReference(User.class, userId),
                viewedAt));
    }
}
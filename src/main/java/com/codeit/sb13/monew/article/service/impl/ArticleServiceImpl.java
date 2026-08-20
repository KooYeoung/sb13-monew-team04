package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import com.codeit.sb13.monew.article.service.ArticleService;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;
import com.codeit.sb13.monew.global.exception.article.ArticleNotFoundException;
import com.codeit.sb13.monew.global.exception.article.ArticleDuplicateException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;

    @Override
    public List<Article> findAll() {
        return articleRepository.findAllByDeletedAtIsNullOrderByDateDesc();
    }

    @Override
    public Article findById(UUID id) {
        return articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    /**
     * 엔티티 저장 (내부용)
     */
    @Override
    @Transactional
    public Article save(Article article) {
        return articleRepository.save(article);
    }

    /**
     * DTO로 새로운 기사 생성
     */
    @Override
    @Transactional
    public Article create(ArticleRequest request) {
        // link 중복 체크
        if (articleRepository.findByLink(request.getLink()).isPresent()) {
            throw new ArticleDuplicateException();
        }

        // Article 생성
        Article article = Article.create(
                request.getTitle(),
                request.getSummary(),
                request.getLink(),
                request.getDate(),
                request.getSource()
        );

        // DataIntegrityViolationException 처리 (동시성 문제 대응)
        try {
            return articleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException e) {
            if (isLinkUniqueViolation(e)) {
                throw new ArticleDuplicateException();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Article article = findById(id);
        article.softDelete();
        articleRepository.save(article);
    }

    private boolean isLinkUniqueViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uk_articles_link");
    }
}
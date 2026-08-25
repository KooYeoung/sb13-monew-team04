package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleRestoreSaveService {

    private final ArticleRepository articleRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Article save(Article article) {
        return articleRepository.saveAndFlush(article);
    }
}

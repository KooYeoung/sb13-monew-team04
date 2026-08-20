package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.service.dto.ArticleRequest;

import java.util.List;
import java.util.UUID;

public interface ArticleService {

    // 새로운 기사 저장 (엔티티)
    Article save(Article article);

    // 새로운 기사 생성 (DTO)
    Article create(ArticleRequest request);

    // 모든 활성 기사 조회 (최신순)
    List<Article> findAll();

    // ID로 기사 조회
    Article findById(UUID id);

    // 기사 삭제 (소프트 딜리트)
    void softDelete(UUID id);
}
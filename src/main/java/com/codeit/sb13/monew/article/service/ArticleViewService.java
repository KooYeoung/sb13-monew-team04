package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.ArticleView;

import java.util.List;
import java.util.UUID;

public interface ArticleViewService {

    // 사용자의 기사 조회 기록 생성 또는 업데이트
    ArticleView recordView(UUID articleId, UUID userId);

    // 특정 기사의 조회수 조회
    long getViewCount(UUID articleId);

    // 특정 사용자의 조회 기록 조회 (최신순)
    List<ArticleView> getUserArticleViews(UUID userId);

    // 기사의 조회 기록 조회 (최신순)
    List<ArticleView> getArticleViews(UUID articleId);
}
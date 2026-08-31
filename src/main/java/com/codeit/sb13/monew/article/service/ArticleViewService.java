package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.service.dto.ArticleViewDto;

import java.util.UUID;

public interface ArticleViewService {

    // 사용자의 기사 조회 기록 생성 또는 업데이트
    ArticleViewDto recordView(UUID articleId, UUID userId);
}

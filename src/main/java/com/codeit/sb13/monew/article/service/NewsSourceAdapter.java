package com.codeit.sb13.monew.article.service;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;

import java.util.List;

/**
 * 뉴스 출처별 기사 수집 어댑터 계약입니다.
 *
 * <p>구현체는 외부 출처 호출, 응답 파싱, 공통 DTO 변환까지만 담당합니다.
 * 반환하는 모든 {@link CollectedArticle}의 {@link CollectedArticle#source()}는
 * {@link #source()}와 일치해야 합니다.
 */
public interface NewsSourceAdapter {
    ArticleSource source();
    List<CollectedArticle> fetch();
}

package com.codeit.sb13.monew.article.repository.dto;

import com.codeit.sb13.monew.article.domain.Article;

/**
 * @param article 기사 엔티티
 * @param viewCount 기사의 총 조회수
 * @param viewedByMe 요청자가 이 기사를 조회한 적 있는지 여부
 */
public record ArticleSearchRow(
        Article article,
        Long viewCount,
        Boolean viewedByMe
) {
}
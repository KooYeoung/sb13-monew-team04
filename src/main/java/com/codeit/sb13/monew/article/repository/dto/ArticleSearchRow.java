package com.codeit.sb13.monew.article.repository.dto;

import com.codeit.sb13.monew.article.domain.Article;

/**
 * @param article 기사 엔티티
 * @param commentCount 기사의 댓글 수. 논리 삭제된 댓글과 탈퇴 사용자의 댓글은 제외한다
 * @param viewCount 기사의 총 조회수. 탈퇴 사용자의 조회 이력은 제외한다
 * @param viewedByMe 요청자가 이 기사를 조회한 적 있는지 여부
 */
public record ArticleSearchRow(
        Article article,
        Long commentCount,
        Long viewCount,
        Boolean viewedByMe
) {
}
package com.codeit.sb13.monew.article.service.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.dto.RecentArticleViewActivityProjection;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentArticleViewDto(
        UUID id,
        UUID viewedBy,
        LocalDateTime createdAt,
        UUID articleId,
        ArticleSource source,
        String sourceUrl,
        String articleTitle,
        LocalDateTime articlePublishedDate,
        String articleSummary,
        Long articleCommentCount,
        Long articleViewCount
) {

    public static RecentArticleViewDto from(RecentArticleViewActivityProjection projection) {
        return new RecentArticleViewDto(
                projection.id(),
                projection.viewedBy(),
                projection.viewedAt(),
                projection.articleId(),
                projection.source(),
                projection.sourceUrl(),
                projection.articleTitle(),
                projection.articlePublishedDate(),
                projection.articleSummary(),
                projection.articleCommentCount(),
                projection.articleViewCount()
        );
    }
}
package com.codeit.sb13.monew.article.repository.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentArticleViewActivityProjection(
        UUID id,
        UUID viewedBy,
        LocalDateTime viewedAt,
        UUID articleId,
        ArticleSource source,
        String sourceUrl,
        String articleTitle,
        LocalDateTime articlePublishedDate,
        String articleSummary,
        Long articleCommentCount,
        Long articleViewCount
) {
}

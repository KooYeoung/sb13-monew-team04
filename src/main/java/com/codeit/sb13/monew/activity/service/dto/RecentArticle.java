package com.codeit.sb13.monew.activity.service.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.RecentArticleViewDto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentArticle(
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
    public static RecentArticle from(RecentArticleViewDto recent) {
        return new RecentArticle(
                recent.id(),
                recent.viewedBy(),
                recent.createdAt(),
                recent.articleId(),
                recent.source(),
                recent.sourceUrl(),
                recent.articleTitle(),
                recent.articlePublishedDate(),
                recent.articleSummary(),
                recent.articleCommentCount(),
                recent.articleViewCount()
        );
    }
}

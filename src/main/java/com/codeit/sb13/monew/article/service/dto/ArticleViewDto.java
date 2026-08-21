package com.codeit.sb13.monew.article.service.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;

import java.time.LocalDateTime;
import java.util.UUID;

public record ArticleViewDto(
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
}
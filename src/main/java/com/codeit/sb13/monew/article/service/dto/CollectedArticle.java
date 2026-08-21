package com.codeit.sb13.monew.article.service.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;

import java.time.LocalDateTime;

public record CollectedArticle(
        ArticleSource source,
        String title,
        String summary,
        String link,
        LocalDateTime publishedAt
) {
}

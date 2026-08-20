package com.codeit.sb13.monew.article.controller.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;

import java.time.LocalDateTime;
import java.util.UUID;

public record ArticleDto(
        UUID id,
        ArticleSource source,
        String sourceUrl,
        String title,
        LocalDateTime publishDate,
        String summary,
        Integer commentCount,
        Integer viewCount,
        boolean viewedByMe
) {
}
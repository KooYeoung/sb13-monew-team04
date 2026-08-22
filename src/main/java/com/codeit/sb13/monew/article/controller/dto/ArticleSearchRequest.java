package com.codeit.sb13.monew.article.controller.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

public record ArticleSearchRequest(
        String keyword,
        List<ArticleSource> sourceIn,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishDateTo
) {
}
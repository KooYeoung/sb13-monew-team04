package com.codeit.sb13.monew.article.controller.dto;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ArticleSearchRequest(
        String keyword,
        List<ArticleSource> sourceIn,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime publishDateTo,
        ArticleOrderBy orderBy,
        Sort.Direction direction,
        String cursor,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
        UUID idAfter,
        int limit
) {
}
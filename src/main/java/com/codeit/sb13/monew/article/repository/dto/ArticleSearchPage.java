package com.codeit.sb13.monew.article.repository.dto;

import java.util.List;

public record ArticleSearchPage(
        List<ArticleSearchRow> rows,
        boolean hasNext,
        long totalElements
) {
}
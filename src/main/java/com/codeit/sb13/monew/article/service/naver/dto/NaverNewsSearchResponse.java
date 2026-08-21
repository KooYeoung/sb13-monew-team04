package com.codeit.sb13.monew.article.service.naver.dto;

import java.util.List;

public record NaverNewsSearchResponse(
        String lastBuildDate,
        Integer total,
        Integer start,
        Integer display,
        List<NaverNewsItem> items
) {
}

package com.codeit.sb13.monew.article.service.naver.dto;

public record NaverNewsItem(
        String title,
        String originallink,
        String link,
        String description,
        String pubDate
) {
}

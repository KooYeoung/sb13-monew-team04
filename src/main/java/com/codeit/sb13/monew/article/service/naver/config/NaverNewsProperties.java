package com.codeit.sb13.monew.article.service.naver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "monew.news.naver")
public record NaverNewsProperties(
        String baseUrl,
        String path,
        String clientId,
        String clientSecret,
        Duration connectTimeout,
        Duration readTimeout
) {
}

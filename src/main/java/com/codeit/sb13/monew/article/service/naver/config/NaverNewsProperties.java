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

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    public NaverNewsProperties {
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }

}

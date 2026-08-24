package com.codeit.sb13.monew.article.s3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monew.s3")
public record S3Properties(
        String bucket,
        String region,
        String endpoint,
        String prefix
) {
}

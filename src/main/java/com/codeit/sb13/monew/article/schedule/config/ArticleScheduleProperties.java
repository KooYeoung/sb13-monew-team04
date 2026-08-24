package com.codeit.sb13.monew.article.schedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monew.backup.schedule")
public record ArticleScheduleProperties(
        boolean enabled,
        String cron
) {
}

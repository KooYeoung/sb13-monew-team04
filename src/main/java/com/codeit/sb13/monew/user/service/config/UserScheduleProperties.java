package com.codeit.sb13.monew.user.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monew.user.schedule")
public record UserScheduleProperties(
    boolean enabled,
    String cron
) {
}
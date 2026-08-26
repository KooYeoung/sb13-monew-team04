package com.codeit.sb13.monew.notification.scheduler.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monew.notification.schedule")
public record NotificationScheduleProperties(
        boolean enabled,
        String cron
) {
}

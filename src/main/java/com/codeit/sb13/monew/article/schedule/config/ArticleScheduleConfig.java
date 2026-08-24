package com.codeit.sb13.monew.article.schedule.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ArticleScheduleProperties.class)
public class ArticleScheduleConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

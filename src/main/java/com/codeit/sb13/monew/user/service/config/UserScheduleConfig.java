package com.codeit.sb13.monew.user.service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UserScheduleProperties.class)
public class UserScheduleConfig {
}
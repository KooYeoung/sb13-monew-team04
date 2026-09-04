package com.codeit.sb13.monew.activity.service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 사용자 활동내역 조회 source 선택 설정을 활성화한다. */
@Configuration
@EnableConfigurationProperties(UserActivityReadProperties.class)
public class UserActivityReadSourceConfig {
}

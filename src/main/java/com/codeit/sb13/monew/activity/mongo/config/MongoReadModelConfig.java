package com.codeit.sb13.monew.activity.mongo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MongoReadModelProperties.class)
public class MongoReadModelConfig {
}

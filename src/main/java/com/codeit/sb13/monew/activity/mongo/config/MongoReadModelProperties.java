package com.codeit.sb13.monew.activity.mongo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monew.mongodb")
public record MongoReadModelProperties(
        boolean enabled,
        boolean initializeIndexes
) {
}

package com.codeit.sb13.monew.article.s3.service.dto;

import org.springframework.util.StringUtils;

import java.time.LocalDate;

public record StorageCommand(
        LocalDate backupDate,
        String content,
        String contentType
) {
    public static final String DEFAULT_CONTENT_TYPE = "application/json; charset=utf-8";

    public StorageCommand {
        if (backupDate == null) {
            throw new IllegalArgumentException("Storage backupDate must not be null");
        }
        content = requireText(content, "content");
        contentType = StringUtils.hasText(contentType) ? contentType : DEFAULT_CONTENT_TYPE;
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Storage " + fieldName + " must not be blank");
        }
        return value;
    }
}

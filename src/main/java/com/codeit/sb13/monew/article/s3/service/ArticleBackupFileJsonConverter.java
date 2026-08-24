package com.codeit.sb13.monew.article.s3.service;

import com.codeit.sb13.monew.article.s3.service.dto.ArticleBackupFile;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileInvalidException;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileJsonException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ArticleBackupFileJsonConverter {
    private final ObjectMapper objectMapper;

    public String serialize(ArticleBackupFile backupFile) {
        if (backupFile == null) {
            throw new ArticleBackupFileInvalidException("backupFile", "백업 파일은 필수입니다.");
        }

        try {
            return objectMapper.writeValueAsString(backupFile);
        } catch (JacksonException e) {
            throw new ArticleBackupFileJsonException("직렬화", e);
        }
    }

    public ArticleBackupFile deserialize(String json) {
        if (!StringUtils.hasText(json)) {
            throw new ArticleBackupFileInvalidException("json", "JSON 문자열은 필수입니다.");
        }

        try {
            return objectMapper.readValue(json, ArticleBackupFile.class);
        } catch (JacksonException | ArticleBackupFileInvalidException e) {
            throw new ArticleBackupFileJsonException("역직렬화", e);
        }
    }
}

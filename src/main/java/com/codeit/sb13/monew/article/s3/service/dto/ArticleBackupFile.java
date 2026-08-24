package com.codeit.sb13.monew.article.s3.service.dto;

import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileInvalidException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ArticleBackupFile(
        Integer schemaVersion,
        LocalDate backupDate,
        LocalDateTime generatedAt,
        Long articleCount,
        List<ArticleBackupItem> articles
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ArticleBackupFile {
        if (schemaVersion == null || schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw invalid("schemaVersion", "스키마 버전은 1이어야 합니다.");
        }
        if (backupDate == null) {
            throw invalid("backupDate", "백업 기준 날짜는 필수입니다.");
        }
        if (generatedAt == null) {
            throw invalid("generatedAt", "백업 파일 생성 시각은 필수입니다.");
        }
        if (articles == null) {
            throw invalid("articles", "기사 목록은 필수입니다.");
        }
        if (articles.stream().anyMatch(Objects::isNull)) {
            throw invalid("articles", "기사 목록에는 빈 항목이 포함될 수 없습니다.");
        }
        if (articleCount == null) {
            throw invalid("articleCount", "기사 수는 필수입니다.");
        }
        if (articleCount != articles.size()) {
            throw invalid("articleCount", "기사 수는 기사 목록 크기와 같아야 합니다.");
        }

        articles = List.copyOf(articles);
    }

    public static ArticleBackupFile of(LocalDate backupDate, LocalDateTime generatedAt, List<ArticleBackupItem> articles) {
        if (articles == null) {
            throw invalid("articles", "기사 목록은 필수입니다.");
        }
        return new ArticleBackupFile(
                CURRENT_SCHEMA_VERSION,
                backupDate,
                generatedAt,
                (long) articles.size(),
                articles
        );
    }

    private static ArticleBackupFileInvalidException invalid(String field, String reason) {
        return new ArticleBackupFileInvalidException(field, reason);
    }

}

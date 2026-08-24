package com.codeit.sb13.monew.article.s3.service.dto;

import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleBackupFileInvalidException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

public record ArticleBackupItem(
        UUID originalArticleId,
        ArticleSource source,
        String link,
        String title,
        String summary,
        LocalDateTime publishedAt,
        LocalDateTime deletedAt
) {
    public ArticleBackupItem {
        if (originalArticleId == null) {
            throw invalid("originalArticleId", "원본 기사 ID는 필수입니다.");
        }
        if (source == null) {
            throw invalid("source", "기사 출처는 필수입니다.");
        }
        if (!StringUtils.hasText(link)) {
            throw invalid("link", "기사 링크는 필수입니다.");
        }
        if (!StringUtils.hasText(title)) {
            throw invalid("title", "기사 제목은 필수입니다.");
        }
        if (!StringUtils.hasText(summary)) {
            throw invalid("summary", "기사 요약은 필수입니다.");
        }
        if (publishedAt == null) {
            throw invalid("publishedAt", "기사 발행일시는 필수입니다.");
        }
    }

    public static ArticleBackupItem from(Article article) {
        if (article == null) {
            throw invalid("article", "백업할 기사는 필수입니다.");
        }
        return new ArticleBackupItem(
                article.getId(),
                article.getSource(),
                article.getLink(),
                article.getTitle(),
                article.getSummary(),
                article.getDate(),
                article.getDeletedAt()
        );
    }

    private static ArticleBackupFileInvalidException invalid(String field, String reason) {
        return new ArticleBackupFileInvalidException(field, reason);
    }
}

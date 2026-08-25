package com.codeit.sb13.monew.article.s3.service.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ArticleRestoreResult(
        LocalDate restoreDate,
        List<UUID> restoredArticleIds,
        long restoredArticleCount
) {

    public ArticleRestoreResult {
        if (restoreDate == null) {
            throw new IllegalArgumentException("restoreDate must not be null");
        }
        if (restoredArticleIds == null) {
            throw new IllegalArgumentException("restoredArticleIds must not be null");
        }
        restoredArticleIds = List.copyOf(restoredArticleIds);
        restoredArticleCount = restoredArticleIds.size();
    }

    public static ArticleRestoreResult of(LocalDate restoreDate, List<UUID> restoredArticleIds) {
        return new ArticleRestoreResult(
                restoreDate,
                restoredArticleIds,
                restoredArticleIds == null ? 0L : restoredArticleIds.size()
        );
    }

    public static ArticleRestoreResult empty(LocalDate restoreDate) {
        return of(restoreDate, List.of());
    }
}

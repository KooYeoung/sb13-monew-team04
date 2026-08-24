package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ArticleBackupDateInvalidException extends ArticleException {

    public ArticleBackupDateInvalidException(String field, String reason) {
        super(ApiErrorCode.ARTICLE_BACKUP_DATE_INVALID, Map.of(
                "field", field,
                "reason", reason
        ));
    }

    public ArticleBackupDateInvalidException(LocalDate from, LocalDate to, String reason) {
        super(ApiErrorCode.ARTICLE_BACKUP_DATE_INVALID, details(from, to, reason));
    }

    private static Map<String, Object> details(LocalDate from, LocalDate to, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("from", from);
        details.put("to", to);
        details.put("reason", reason);
        return details;
    }
}

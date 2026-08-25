package com.codeit.sb13.monew.global.exception.article;

import com.codeit.sb13.monew.global.exception.ApiErrorCode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ArticleRestoreDateInvalidException extends ArticleException {

    public ArticleRestoreDateInvalidException(LocalDate from, LocalDate to, String reason) {
        super(ApiErrorCode.ARTICLE_RESTORE_DATE_INVALID, details(from, to, reason));
    }

    private static Map<String, Object> details(LocalDate from, LocalDate to, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("from", from);
        details.put("to", to);
        details.put("reason", reason);
        return details;
    }
}

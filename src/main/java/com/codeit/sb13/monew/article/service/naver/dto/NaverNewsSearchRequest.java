package com.codeit.sb13.monew.article.service.naver.dto;

import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.springframework.util.StringUtils;

public record NaverNewsSearchRequest(
        String query,
        Integer start,
        Integer display,
        NaverNewsSort sort
) {

    private static final int DEFAULT_DISPLAY = 10;
    private static final int MIN_DISPLAY = 1;
    private static final int MAX_DISPLAY = 100;
    private static final int DEFAULT_START = 1;
    private static final int MIN_START = 1;
    private static final int MAX_START = 1000;

    public NaverNewsSearchRequest {
        if (!StringUtils.hasText(query)) {
            throw new ArticleFetchRequestInvalidException("query");
        }
        display = display == null ? DEFAULT_DISPLAY : display;
        start = start == null ? DEFAULT_START : start;
        sort = sort == null ? NaverNewsSort.SIM : sort;

        if (display < MIN_DISPLAY || display > MAX_DISPLAY) {
            throw new ArticleFetchRequestInvalidException("display");
        }
        if (start < MIN_START || start > MAX_START) {
            throw new ArticleFetchRequestInvalidException("start");
        }

    }

    public NaverNewsSearchRequest(String query) {
        this(query, null, null, null);
    }

    public NaverNewsSearchRequest(String query, NaverNewsSort sort) {
        this(query, null, null, sort);
    }

    public NaverNewsSearchRequest(String query, NaverNewsSort sort, Integer display) {
        this(query, null, display, sort);
    }
}

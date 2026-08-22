package com.codeit.sb13.monew.article.service.rss.category;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum HankyungRssCategory implements RssNewsCategory {
    ALL_NEWS("all-news", "전체뉴스"),
    FINANCE("finance", "증권"),
    ECONOMY("economy", "경제"),
    REALESTATE("realestate", "부동산"),
    IT("it", "IT"),
    POLITICS("politics", "정치"),
    INTERNATIONAL("international", "국제"),
    SOCIETY("society", "사회"),
    LIFE("life", "생활"),
    OPINION("opinion", "오피니언"),
    SPORTS("sports", "스포츠"),
    ENTERTAINMENT("entertainment", "연예"),
    VIDEO("video", "VIDEO");

    private final String key;
    private final String label;
    private static final Map<String, HankyungRssCategory> CATEGORY_BY_KEY = Arrays.stream(HankyungRssCategory.values())
            .collect(Collectors.toUnmodifiableMap(HankyungRssCategory::key, Function.identity()));

    HankyungRssCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static HankyungRssCategory fromKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new ArticleFetchRequestInvalidException("hankyung category");
        }

        String normalizedKey = key.strip().toLowerCase(Locale.ROOT);
        HankyungRssCategory hankyungRssCategory = CATEGORY_BY_KEY.get(normalizedKey);

        if (hankyungRssCategory == null) {
            throw new ArticleFetchRequestInvalidException("hankyung category");
        }
        return hankyungRssCategory;
    }

    @Override
    public ArticleSource source() {
        return ArticleSource.HANKYUNG;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String label() {
        return label;
    }
}

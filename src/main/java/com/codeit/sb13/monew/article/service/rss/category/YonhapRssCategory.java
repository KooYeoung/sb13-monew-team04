package com.codeit.sb13.monew.article.service.rss.category;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum YonhapRssCategory implements RssNewsCategory {
    LATEST("latest", "최신"),
    POLITICS("politics", "정치"),
    ECONOMY("economy", "경제"),
    BIZN("bizn", "비즈&"),
    STOCKS("stocks", "증권"),
    SOCIETY("society", "사회"),
    LOCAL("local", "지역"),
    INTERNATIONAL("international", "세계"),
    CULTURE("culture", "문화ㆍ연예"),
    SPORTS("sports", "스포츠"),
    WEATHER("weather", "날씨");

    private final String key;
    private final String label;

    private static final Map<String, YonhapRssCategory> CATEGORY_BY_KEY = Arrays.stream(YonhapRssCategory.values())
            .collect(Collectors.toUnmodifiableMap(YonhapRssCategory::key, Function.identity()));

    YonhapRssCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static YonhapRssCategory fromKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new ArticleFetchRequestInvalidException("yonhap category");
        }

        String normalizedKey = key.strip().toLowerCase(Locale.ROOT);
        YonhapRssCategory yonhapRssCategory = CATEGORY_BY_KEY.get(normalizedKey);

        if (yonhapRssCategory == null) {
            throw new ArticleFetchRequestInvalidException("yonhap category");
        }
        return yonhapRssCategory;
    }

    @Override
    public ArticleSource source() {
        return ArticleSource.YEONHAP;
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

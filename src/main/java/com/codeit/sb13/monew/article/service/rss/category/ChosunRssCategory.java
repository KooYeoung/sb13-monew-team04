package com.codeit.sb13.monew.article.service.rss.category;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ChosunRssCategory implements RssNewsCategory {
    ALL("all", "전체기사"),
    POLITICS("politics", "정치"),
    ECONOMY("economy", "경제"),
    NATIONAL("national", "사회"),
    INTERNATIONAL("international", "국제"),
    CULTURE_LIFE("culture-life", "문화/라이프"),
    OPINION("opinion", "오피니언"),
    SPORTS("sports", "스포츠"),
    ENTERTAINMENTS("entertainments", "연예");

    private final String key;
    private final String label;

    private static final Map<String, ChosunRssCategory> CATEGORY_BY_KEY = Arrays.stream(ChosunRssCategory.values())
            .collect(Collectors.toUnmodifiableMap(ChosunRssCategory::key, Function.identity()));

    ChosunRssCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static ChosunRssCategory fromKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new ArticleFetchRequestInvalidException("chosun category");
        }

        String normalizedKey = key.strip().toLowerCase(Locale.ROOT);
        ChosunRssCategory chosunRssCategory = CATEGORY_BY_KEY.get(normalizedKey);

        if (chosunRssCategory == null) {
            throw new ArticleFetchRequestInvalidException("chosun category");
        }
        return chosunRssCategory;
    }

    @Override
    public ArticleSource source() {
        return ArticleSource.CHOSUN;
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

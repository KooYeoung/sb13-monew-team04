package com.codeit.sb13.monew.article.service.naver.mapper;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsItem;
import com.codeit.sb13.monew.article.service.naver.dto.NaverNewsSearchResponse;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class NaverNewsMapper {

    private static final DateTimeFormatter RFC_1123_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final String B_TAG_PATTERN = "(?i)</?b>";

    public List<CollectedArticle> toCollectedArticles(NaverNewsSearchResponse response) {

        return response.items()
                .stream()
                .filter(this::hasLink)
                .map(this::mapToCollectedArticle)
                .toList();
    }


    private boolean hasLink(NaverNewsItem item) {
        return StringUtils.hasText(item.originallink()) || StringUtils.hasText(item.link());
    }

    private CollectedArticle mapToCollectedArticle(NaverNewsItem item) {
        return new CollectedArticle(
                ArticleSource.NAVER,
                clean(item.title()),
                clean(item.description()),
                clean(getOriginalLinkOrFallback(item)),
                convertPublicationDate(item)
        );
    }

    private LocalDateTime convertPublicationDate(NaverNewsItem item) {

        try {
            return LocalDateTime.parse(item.pubDate(), RFC_1123_DATE_FORMATTER);
        } catch (Exception e) {
            throw new ArticleFetchParseException(ArticleSource.NAVER.name(), e);
        }

    }

    private String getOriginalLinkOrFallback(NaverNewsItem item) {
        if (StringUtils.hasText(item.originallink())) {
            return item.originallink();
        }

        return item.link();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        return StringEscapeUtils.unescapeHtml4(value)
                .replaceAll(B_TAG_PATTERN, "");
    }

}

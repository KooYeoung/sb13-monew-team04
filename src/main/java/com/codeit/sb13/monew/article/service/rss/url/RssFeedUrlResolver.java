package com.codeit.sb13.monew.article.service.rss.url;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RssFeedUrlResolver {
    public String resolve(ArticleSource source, String baseUrl, RssNewsCategory category) {
        if (source == null) {
            throw new ArticleFetchRequestInvalidException("source");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new ArticleFetchRequestInvalidException("baseUrl");
        }
        if (category == null) {
            throw new ArticleFetchRequestInvalidException("category");
        }
        if (category.source() != source) {
            throw new ArticleFetchRequestInvalidException("category");
        }

        String normalizedBaseUrl = baseUrl.strip();

        return switch (source) {
            case HANKYUNG -> normalizedBaseUrl + "/" + category.key();
            case CHOSUN -> {
                if (category.key().equals("all")) {
                    yield normalizedBaseUrl + "/?outputType=xml";
                }
                yield normalizedBaseUrl + "/category/" + category.key() + "/?outputType=xml";
            }
            case YEONHAP -> {
                if (category.key().equals("latest")) {
                    yield normalizedBaseUrl + "/browse/feed/";
                }
                yield normalizedBaseUrl + "/category/news/" + category.key() + "/feed/";
            }
            case NAVER -> throw new ArticleFetchRequestInvalidException("source");
        };
    }
}

package com.codeit.sb13.monew.article.service.rss.client;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import com.codeit.sb13.monew.article.service.rss.config.RssNewsProperties;
import com.codeit.sb13.monew.article.service.rss.mapper.RssNewsMapper;
import com.codeit.sb13.monew.article.service.rss.url.RssFeedUrlResolver;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchFailedException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchParseException;
import com.codeit.sb13.monew.global.exception.article.ArticleFetchRequestInvalidException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RestClientRssNewsClient implements RssNewsClient {

    private final RestClient rssNewsRestClient;
    private final RssNewsProperties props;
    private final RssFeedUrlResolver urlResolver;
    private final RssNewsMapper rssNewsMapper;

    @Override
    public List<CollectedArticle> fetch(ArticleSource source, RssNewsCategory category) {
        validateRequest(source, category);

        String baseUrl = getBaseUrl(source);
        String resolvedUrl = urlResolver.resolve(source, baseUrl, category);
        try {
            String body = rssNewsRestClient.get()
                    .uri(resolvedUrl)
                    .retrieve()
                    .body(String.class);

            return rssNewsMapper.toCollectedArticles(source, body);
        } catch (ArticleFetchParseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ArticleFetchFailedException(source.name(), e);
        }
    }

    private void validateRequest(ArticleSource source, RssNewsCategory category) {
        if (source == null) {
            throw new ArticleFetchRequestInvalidException("source");
        }
        if (category == null) {
            throw new ArticleFetchRequestInvalidException("category");
        }
        if (category.source() != source) {
            throw new ArticleFetchRequestInvalidException("category");
        }
    }

    private String getBaseUrl(ArticleSource source) {
        return switch (source) {
            case YEONHAP -> props.yonhap().baseUrl();
            case HANKYUNG -> props.hankyung().baseUrl();
            case CHOSUN -> props.chosun().baseUrl();
            default -> throw new ArticleFetchRequestInvalidException("source");
        };
    }
}

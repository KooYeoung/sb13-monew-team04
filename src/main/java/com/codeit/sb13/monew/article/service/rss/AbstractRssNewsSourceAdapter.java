package com.codeit.sb13.monew.article.service.rss;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import com.codeit.sb13.monew.article.service.rss.client.RssNewsClient;

import java.util.Collection;
import java.util.List;

public abstract class AbstractRssNewsSourceAdapter implements RssNewsSourceAdapter {

    private final ArticleSource source;
    private final RssNewsCategory defaultCategory;
    private final RssNewsClient client;

    protected AbstractRssNewsSourceAdapter(
            ArticleSource source,
            RssNewsCategory defaultCategory,
            RssNewsClient client
    ) {
        this.source = source;
        this.defaultCategory = defaultCategory;
        this.client = client;
    }

    @Override
    public ArticleSource source() {
        return source;
    }

    @Override
    public List<CollectedArticle> fetch() {
        return client.fetch(source(), defaultCategory);
    }

    @Override
    public List<CollectedArticle> fetch(List<String> categoryKeys) {
        if (categoryKeys == null || categoryKeys.isEmpty()) {
            return List.of();
        }

        List<RssNewsCategory> categories = categoryKeys.stream()
                .map(this::toCategory)
                .toList();

        return categories.stream()
                .map(category -> client.fetch(source(), category))
                .flatMap(Collection::stream)
                .toList();
    }

    protected abstract RssNewsCategory toCategory(String key);
}

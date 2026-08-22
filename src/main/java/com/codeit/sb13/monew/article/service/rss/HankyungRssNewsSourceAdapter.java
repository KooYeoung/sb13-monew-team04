package com.codeit.sb13.monew.article.service.rss;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.rss.category.HankyungRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import com.codeit.sb13.monew.article.service.rss.client.RssNewsClient;
import org.springframework.stereotype.Component;

@Component
public class HankyungRssNewsSourceAdapter extends AbstractRssNewsSourceAdapter {

    public HankyungRssNewsSourceAdapter(RssNewsClient client) {
        super(ArticleSource.HANKYUNG, HankyungRssCategory.ALL_NEWS, client);
    }

    @Override
    protected RssNewsCategory toCategory(String key) {
        return HankyungRssCategory.fromKey(key);
    }
}

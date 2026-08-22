package com.codeit.sb13.monew.article.service.rss;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.rss.category.ChosunRssCategory;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import com.codeit.sb13.monew.article.service.rss.client.RssNewsClient;
import org.springframework.stereotype.Component;

@Component
public class ChosunRssNewsSourceAdapter extends AbstractRssNewsSourceAdapter {

    public ChosunRssNewsSourceAdapter(RssNewsClient client) {
        super(ArticleSource.CHOSUN, ChosunRssCategory.ALL, client);
    }

    @Override
    protected RssNewsCategory toCategory(String key) {
        return ChosunRssCategory.fromKey(key);
    }
}

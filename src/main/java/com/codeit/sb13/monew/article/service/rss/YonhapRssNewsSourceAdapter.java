package com.codeit.sb13.monew.article.service.rss;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;
import com.codeit.sb13.monew.article.service.rss.category.YonhapRssCategory;
import com.codeit.sb13.monew.article.service.rss.client.RssNewsClient;
import org.springframework.stereotype.Component;

@Component
public class YonhapRssNewsSourceAdapter extends AbstractRssNewsSourceAdapter {

    public YonhapRssNewsSourceAdapter(RssNewsClient client) {
        super(ArticleSource.YEONHAP, YonhapRssCategory.LATEST, client);
    }

    @Override
    protected RssNewsCategory toCategory(String key) {
        return YonhapRssCategory.fromKey(key);
    }
}

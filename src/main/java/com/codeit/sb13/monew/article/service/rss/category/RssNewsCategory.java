package com.codeit.sb13.monew.article.service.rss.category;

import com.codeit.sb13.monew.article.domain.ArticleSource;

public interface RssNewsCategory {
    ArticleSource source();

    String key();

    String label();
}

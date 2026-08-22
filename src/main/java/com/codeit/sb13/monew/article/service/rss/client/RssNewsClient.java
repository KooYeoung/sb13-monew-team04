package com.codeit.sb13.monew.article.service.rss.client;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;
import com.codeit.sb13.monew.article.service.rss.category.RssNewsCategory;

import java.util.List;

public interface RssNewsClient {
    List<CollectedArticle> fetch(ArticleSource source, RssNewsCategory category);
}

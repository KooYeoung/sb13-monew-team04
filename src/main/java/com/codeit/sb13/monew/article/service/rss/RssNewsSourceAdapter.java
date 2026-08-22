package com.codeit.sb13.monew.article.service.rss;

import com.codeit.sb13.monew.article.service.NewsSourceAdapter;
import com.codeit.sb13.monew.article.service.dto.CollectedArticle;

import java.util.List;

public interface RssNewsSourceAdapter extends NewsSourceAdapter {
    List<CollectedArticle> fetch(List<String> categoryKeys);
}

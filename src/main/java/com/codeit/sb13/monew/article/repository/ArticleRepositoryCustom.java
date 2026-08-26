package com.codeit.sb13.monew.article.repository;

import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchPage;

public interface ArticleRepositoryCustom {

    ArticleSearchPage search(ArticleSearchCondition condition);
}
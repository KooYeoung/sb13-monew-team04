package com.codeit.sb13.monew.article.repository;

import com.codeit.sb13.monew.article.repository.dto.ArticleSearchCondition;
import com.codeit.sb13.monew.article.repository.dto.ArticleSearchRow;

import java.util.List;

public interface ArticleRepositoryCustom {

    List<ArticleSearchRow> search(ArticleSearchCondition condition);
}
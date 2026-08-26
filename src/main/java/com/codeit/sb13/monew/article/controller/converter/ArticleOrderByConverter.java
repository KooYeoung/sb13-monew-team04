package com.codeit.sb13.monew.article.controller.converter;

import com.codeit.sb13.monew.article.service.dto.ArticleOrderBy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ArticleOrderByConverter implements Converter<String, ArticleOrderBy> {

    @Override
    public ArticleOrderBy convert(String source) {
        return ArticleOrderBy.from(source);
    }
}
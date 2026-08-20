package com.codeit.sb13.monew.article.mapper;

import com.codeit.sb13.monew.article.controller.dto.ArticleDto;
import com.codeit.sb13.monew.article.controller.dto.ArticleViewDto;
import com.codeit.sb13.monew.article.domain.Article;
import com.codeit.sb13.monew.article.domain.ArticleView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    @Mapping(source = "article.link", target = "sourceUrl")
    @Mapping(source = "article.date", target = "publishDate")
    ArticleDto toDto(Article article, boolean viewedByMe,
                     Integer commentCount, Integer viewCount);

    @Mapping(source = "articleView.user.id", target = "viewedBy")
    @Mapping(source = "articleView.article.id", target = "articleId")
    @Mapping(source = "articleView.article.source", target = "source")
    @Mapping(source = "articleView.article.link", target = "sourceUrl")
    @Mapping(source = "articleView.article.title", target = "articleTitle")
    @Mapping(source = "articleView.article.date", target = "articlePublishedDate")
    @Mapping(source = "articleView.article.summary", target = "articleSummary")
    @Mapping(source = "commentCount", target = "articleCommentCount")
    @Mapping(source = "viewCount", target = "articleViewCount")
    ArticleViewDto toViewDto(ArticleView articleView,
                             Integer commentCount, Integer viewCount);
}
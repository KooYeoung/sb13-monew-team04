package com.codeit.sb13.monew.article.service.impl;

import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.service.dto.RecentArticleViewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArticleViewActivityService {

    private final ArticleViewRepository articleViewRepository;

    public List<RecentArticleViewDto> getRecentArticleViews(UUID userId) {
        return articleViewRepository.findRecentArticleViewActivities(userId)
                .stream()
                .map(RecentArticleViewDto::from)
                .toList();

    }
}

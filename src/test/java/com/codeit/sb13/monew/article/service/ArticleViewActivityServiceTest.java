package com.codeit.sb13.monew.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.repository.ArticleViewRepository;
import com.codeit.sb13.monew.article.repository.dto.RecentArticleViewActivityProjection;
import com.codeit.sb13.monew.article.service.dto.RecentArticleViewDto;
import com.codeit.sb13.monew.article.service.impl.ArticleViewActivityService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArticleViewActivityServiceTest {

    @Mock
    private ArticleViewRepository articleViewRepository;

    @InjectMocks
    private ArticleViewActivityService articleViewActivityService;

    @Test
    @DisplayName("getRecentArticleViews maps repository projections to DTOs")
    void getRecentArticleViews_mapsProjectionToDto() {
        UUID userId = UUID.randomUUID();
        RecentArticleViewActivityProjection projection = new RecentArticleViewActivityProjection(
                UUID.randomUUID(),
                userId,
                LocalDateTime.of(2026, 8, 25, 13, 0),
                UUID.randomUUID(),
                ArticleSource.NAVER,
                "https://example.com/article",
                "article title",
                LocalDateTime.of(2026, 8, 25, 12, 0),
                "summary",
                7L,
                20L
        );
        given(articleViewRepository.findRecentArticleViewActivities(userId)).willReturn(List.of(projection));

        List<RecentArticleViewDto> result = articleViewActivityService.getRecentArticleViews(userId);

        assertThat(result).singleElement()
                .satisfies(dto -> {
                    assertThat(dto.id()).isEqualTo(projection.id());
                    assertThat(dto.viewedBy()).isEqualTo(userId);
                    assertThat(dto.createdAt()).isEqualTo(projection.viewedAt());
                    assertThat(dto.articleId()).isEqualTo(projection.articleId());
                    assertThat(dto.source()).isEqualTo(ArticleSource.NAVER);
                    assertThat(dto.sourceUrl()).isEqualTo("https://example.com/article");
                    assertThat(dto.articleTitle()).isEqualTo("article title");
                    assertThat(dto.articlePublishedDate()).isEqualTo(projection.articlePublishedDate());
                    assertThat(dto.articleSummary()).isEqualTo("summary");
                    assertThat(dto.articleCommentCount()).isEqualTo(7L);
                    assertThat(dto.articleViewCount()).isEqualTo(20L);
                });
        then(articleViewRepository).should().findRecentArticleViewActivities(userId);
    }
}

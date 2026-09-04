package com.codeit.sb13.monew.activity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.activity.service.UserActivitySections;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.RecentArticleViewDto;
import com.codeit.sb13.monew.article.service.impl.ArticleViewActivityService;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentActivityDto;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentLikeActivityDto;
import com.codeit.sb13.monew.comment.service.impl.CommentActivityService;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeActivityService;
import com.codeit.sb13.monew.interest.service.dto.SubscribedInterestActivityDto;
import com.codeit.sb13.monew.interest.service.impl.SubscribedActivityService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RdbUserActivityReadSourceTest {

    @Mock
    ArticleViewActivityService articleViewActivityService;
    @Mock
    CommentActivityService commentActivityService;
    @Mock
    CommentLikeActivityService commentLikeActivityService;
    @Mock
    SubscribedActivityService subscribedActivityService;
    @InjectMocks
    RdbUserActivityReadSource source;

    @Test
    void mapsExistingRdbActivityDtosToSharedSections() {
        UUID userId = UUID.randomUUID();
        UUID articleId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 15, 0);
        when(subscribedActivityService.getSubscribedInterestActivities(userId)).thenReturn(List.of(
                new SubscribedInterestActivityDto(
                        UUID.randomUUID(), now, UUID.randomUUID(),
                        "interest", List.of("keyword"), 3L
                )
        ));
        when(commentActivityService.getRecentCommentActivities(userId)).thenReturn(List.of(
                new RecentCommentActivityDto(
                        commentId, articleId, "article", userId,
                        "user", "content", 4L, now
                )
        ));
        when(commentLikeActivityService.getRecentCommentLikes(userId)).thenReturn(List.of(
                new RecentCommentLikeActivityDto(
                        UUID.randomUUID(), now, commentId, articleId, "article",
                        UUID.randomUUID(), "author", "content", 4L, now.minusDays(1)
                )
        ));
        when(articleViewActivityService.getRecentArticleViews(userId)).thenReturn(List.of(
                new RecentArticleViewDto(
                        UUID.randomUUID(), userId, now, articleId, ArticleSource.NAVER,
                        "https://example.com/article", "article", now.minusDays(1),
                        "summary", 8L, 9L
                )
        ));

        UserActivitySections sections = source.read(userId);

        assertThat(sections.subscriptions()).singleElement()
                .hasFieldOrPropertyWithValue("interestSubscriberCount", 3L);
        assertThat(sections.comments()).singleElement()
                .hasFieldOrPropertyWithValue("likeCount", 4L);
        assertThat(sections.commentLikes()).singleElement()
                .hasFieldOrPropertyWithValue("commentId", commentId);
        assertThat(sections.articleViews()).singleElement()
                .satisfies(article -> {
                    assertThat(article.articleCommentCount()).isEqualTo(8L);
                    assertThat(article.articleViewCount()).isEqualTo(9L);
                });
    }
}

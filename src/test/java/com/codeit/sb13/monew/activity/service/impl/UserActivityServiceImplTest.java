package com.codeit.sb13.monew.activity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeit.sb13.monew.activity.service.dto.UserActivityDto;
import com.codeit.sb13.monew.article.domain.ArticleSource;
import com.codeit.sb13.monew.article.service.dto.RecentArticleViewDto;
import com.codeit.sb13.monew.article.service.impl.ArticleViewActivityService;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentActivityDto;
import com.codeit.sb13.monew.comment.service.dto.RecentCommentLikeActivityDto;
import com.codeit.sb13.monew.comment.service.impl.CommentActivityService;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeActivityService;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.interest.service.dto.SubscribedInterestActivityDto;
import com.codeit.sb13.monew.interest.service.impl.SubscribedActivityService;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceImplTest {

    @Mock
    UserService userService;

    @Mock
    ArticleViewActivityService articleViewActivityService;

    @Mock
    CommentActivityService commentActivityService;

    @Mock
    CommentLikeActivityService commentLikeActivityService;

    @Mock
    SubscribedActivityService subscribedActivityService;

    @InjectMocks
    UserActivityServiceImpl userActivityService;

    @Test
    @DisplayName("userActivity composes activity from domain activity services")
    void userActivity_activeUser_returnsComposedActivity() {
        UUID userId = UUID.randomUUID();
        LocalDateTime userCreatedAt = LocalDateTime.of(2026, 8, 25, 13, 0);
        User user = user(userId, userCreatedAt);
        SubscribedInterestActivityDto subscription = new SubscribedInterestActivityDto(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 25, 13, 1),
                UUID.randomUUID(),
                "sports",
                List.of("football", "baseball"),
                12L
        );
        RecentCommentActivityDto comment = new RecentCommentActivityDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "comment article",
                userId,
                "tester",
                "hello",
                3L,
                LocalDateTime.of(2026, 8, 25, 13, 2)
        );
        RecentCommentLikeActivityDto commentLike = new RecentCommentLikeActivityDto(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 8, 25, 13, 3),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "liked article",
                UUID.randomUUID(),
                "commenter",
                "liked comment",
                5L,
                LocalDateTime.of(2026, 8, 25, 13, 4)
        );
        RecentArticleViewDto articleView = new RecentArticleViewDto(
                UUID.randomUUID(),
                userId,
                LocalDateTime.of(2026, 8, 25, 13, 5),
                UUID.randomUUID(),
                ArticleSource.NAVER,
                "https://example.com/article",
                "viewed article",
                LocalDateTime.of(2026, 8, 25, 12, 0),
                "summary",
                7L,
                20L
        );

        when(userService.findById(userId)).thenReturn(user);
        when(subscribedActivityService.getSubscribedInterestActivities(userId)).thenReturn(List.of(subscription));
        when(commentActivityService.getRecentCommentActivities(userId)).thenReturn(List.of(comment));
        when(commentLikeActivityService.getRecentCommentLikes(userId)).thenReturn(List.of(commentLike));
        when(articleViewActivityService.getRecentArticleViews(userId)).thenReturn(List.of(articleView));

        UserActivityDto result = userActivityService.userActivity(userId);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.nickname()).isEqualTo("tester");
        assertThat(result.createdAt()).isEqualTo(userCreatedAt);
        assertThat(result.subscriptions()).singleElement()
                .satisfies(recent -> {
                    assertThat(recent.id()).isEqualTo(subscription.id());
                    assertThat(recent.interestName()).isEqualTo("sports");
                    assertThat(recent.interestKeywords()).containsExactly("football", "baseball");
                });
        assertThat(result.comments()).singleElement()
                .satisfies(recent -> {
                    assertThat(recent.id()).isEqualTo(comment.id());
                    assertThat(recent.content()).isEqualTo("hello");
                });
        assertThat(result.commentLikes()).singleElement()
                .satisfies(recent -> {
                    assertThat(recent.id()).isEqualTo(commentLike.id());
                    assertThat(recent.commentContent()).isEqualTo("liked comment");
                });
        assertThat(result.articleViews()).singleElement()
                .satisfies(recent -> {
                    assertThat(recent.id()).isEqualTo(articleView.id());
                    assertThat(recent.articleTitle()).isEqualTo("viewed article");
                });
        verify(userService).findById(userId);
        verify(subscribedActivityService).getSubscribedInterestActivities(userId);
        verify(commentActivityService).getRecentCommentActivities(userId);
        verify(commentLikeActivityService).getRecentCommentLikes(userId);
        verify(articleViewActivityService).getRecentArticleViews(userId);
    }

    @Test
    @DisplayName("userActivity returns empty lists when user has no activity")
    void userActivity_noActivity_returnsEmptyLists() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, LocalDateTime.of(2026, 8, 25, 13, 0));

        when(userService.findById(userId)).thenReturn(user);
        when(subscribedActivityService.getSubscribedInterestActivities(userId)).thenReturn(List.of());
        when(commentActivityService.getRecentCommentActivities(userId)).thenReturn(List.of());
        when(commentLikeActivityService.getRecentCommentLikes(userId)).thenReturn(List.of());
        when(articleViewActivityService.getRecentArticleViews(userId)).thenReturn(List.of());

        UserActivityDto result = userActivityService.userActivity(userId);

        assertThat(result.subscriptions()).isEmpty();
        assertThat(result.comments()).isEmpty();
        assertThat(result.commentLikes()).isEmpty();
        assertThat(result.articleViews()).isEmpty();
    }

    @Test
    @DisplayName("userActivity throws UserNotFoundException for soft-deleted user")
    void userActivity_deletedUser_throwsUserNotFoundException() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, LocalDateTime.of(2026, 8, 25, 13, 0));
        user.softDelete();
        when(userService.findById(userId)).thenReturn(user);

        assertThatThrownBy(() -> userActivityService.userActivity(userId))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(
                subscribedActivityService,
                commentActivityService,
                commentLikeActivityService,
                articleViewActivityService
        );
    }

    private User user(UUID id, LocalDateTime createdAt) {
        User user = User.builder()
                .email("user@example.com")
                .nickname("tester")
                .password("PassWord123!")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        return user;
    }
}

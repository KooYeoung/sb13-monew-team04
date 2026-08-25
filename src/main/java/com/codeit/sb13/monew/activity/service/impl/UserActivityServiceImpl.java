package com.codeit.sb13.monew.activity.service.impl;

import com.codeit.sb13.monew.activity.service.UserActivityService;
import com.codeit.sb13.monew.activity.service.dto.*;
import com.codeit.sb13.monew.article.service.impl.ArticleViewActivityService;
import com.codeit.sb13.monew.comment.service.impl.CommentActivityService;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeActivityService;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.interest.service.impl.SubscribedActivityService;
import com.codeit.sb13.monew.user.domain.User;
import com.codeit.sb13.monew.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

    private final UserService userService;
    private final ArticleViewActivityService articleViewActivityService;
    private final CommentActivityService commentActivityService;
    private final CommentLikeActivityService commentLikeActivityService;
    private final SubscribedActivityService subscribedActivityService;

    @Override
    public UserActivityDto userActivity(UUID userId) {
        User currentUser = findUserOrThrow(userId);
        List<RecentArticle> userRecentArticles = getRecentArticlesForUser(userId);
        List<RecentComment> userRecentComments = getRecentCommentsForUser(userId);
        List<RecentCommentLike> userRecentLikes = getRecentCommentLikesForUser(userId);
        List<RecentSubscribed> userRecentSubscriptions = getRecentSubscribedActivitiesForUser(userId);

        return UserActivityDto.of(
                currentUser.getId(),
                currentUser.getEmail(),
                currentUser.getNickname(),
                currentUser.getCreatedAt(),
                userRecentSubscriptions,
                userRecentComments,
                userRecentLikes,
                userRecentArticles
        );
    }

    private User findUserOrThrow(UUID userId) {
        User currentUser = userService.findById(userId);
        if (currentUser.getDeletedAt() != null) {
            throw new UserNotFoundException(userId);
        }
        return currentUser;
    }

    private List<RecentSubscribed> getRecentSubscribedActivitiesForUser(UUID userId) {
        return subscribedActivityService.getSubscribedInterestActivities(userId)
                .stream()
                .map(RecentSubscribed::from)
                .toList();
    }

    private List<RecentCommentLike> getRecentCommentLikesForUser(UUID userId) {
        return commentLikeActivityService.getRecentCommentLikes(userId)
                .stream()
                .map(RecentCommentLike::from)
                .toList();
    }

    private List<RecentComment> getRecentCommentsForUser(UUID userId) {
        return commentActivityService.getRecentCommentActivities(userId)
                .stream()
                .map(RecentComment::from)
                .toList();
    }

    private List<RecentArticle> getRecentArticlesForUser(UUID userId) {
        return articleViewActivityService.getRecentArticleViews(userId)
                .stream()
                .map(RecentArticle::from)
                .toList();
    }
}

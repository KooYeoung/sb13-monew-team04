package com.codeit.sb13.monew.activity.service.impl;

import com.codeit.sb13.monew.activity.service.UserActivityReadSource;
import com.codeit.sb13.monew.activity.service.UserActivitySections;
import com.codeit.sb13.monew.activity.service.dto.RecentArticle;
import com.codeit.sb13.monew.activity.service.dto.RecentComment;
import com.codeit.sb13.monew.activity.service.dto.RecentCommentLike;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import com.codeit.sb13.monew.article.service.impl.ArticleViewActivityService;
import com.codeit.sb13.monew.comment.service.impl.CommentActivityService;
import com.codeit.sb13.monew.comment.service.impl.CommentLikeActivityService;
import com.codeit.sb13.monew.interest.service.impl.SubscribedActivityService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 기존 RDB 활동 조회 서비스 네 개를 공통 활동 조회 계약으로 조합한다. */
@Component
@RequiredArgsConstructor
public class RdbUserActivityReadSource implements UserActivityReadSource {

    private final ArticleViewActivityService articleViewActivityService;
    private final CommentActivityService commentActivityService;
    private final CommentLikeActivityService commentLikeActivityService;
    private final SubscribedActivityService subscribedActivityService;

    @Override
    public UserActivitySections read(UUID userId) {
        return new UserActivitySections(
                subscribedActivityService.getSubscribedInterestActivities(userId).stream()
                        .map(RecentSubscribed::from)
                        .toList(),
                commentActivityService.getRecentCommentActivities(userId).stream()
                        .map(RecentComment::from)
                        .toList(),
                commentLikeActivityService.getRecentCommentLikes(userId).stream()
                        .map(RecentCommentLike::from)
                        .toList(),
                articleViewActivityService.getRecentArticleViews(userId).stream()
                        .map(RecentArticle::from)
                        .toList()
        );
    }
}

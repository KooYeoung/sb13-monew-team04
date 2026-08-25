package com.codeit.sb13.monew.activity.service.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserActivityDto(
        UUID id,
        String email,
        String nickname,
        LocalDateTime createdAt,
        List<RecentSubscribed> subscriptions,
        List<RecentComment> comments,
        List<RecentCommentLike> commentLikes,
        List<RecentArticle> articleViews
) {
    public static UserActivityDto of(
            UUID id,
            String email,
            String nickname,
            LocalDateTime createdAt,
            List<RecentSubscribed> subscriptions,
            List<RecentComment> comments,
            List<RecentCommentLike> commentLikes,
            List<RecentArticle> articleViews) {
        return new UserActivityDto(id, email, nickname, createdAt, subscriptions, comments, commentLikes, articleViews);
    }
}

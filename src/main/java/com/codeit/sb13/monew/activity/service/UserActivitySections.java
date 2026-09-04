package com.codeit.sb13.monew.activity.service;

import com.codeit.sb13.monew.activity.service.dto.RecentArticle;
import com.codeit.sb13.monew.activity.service.dto.RecentComment;
import com.codeit.sb13.monew.activity.service.dto.RecentCommentLike;
import com.codeit.sb13.monew.activity.service.dto.RecentSubscribed;
import java.util.List;

/** 사용자 활동내역 응답에서 사용자 기본 정보를 제외한 네 활동 목록이다. */
public record UserActivitySections(
        List<RecentSubscribed> subscriptions,
        List<RecentComment> comments,
        List<RecentCommentLike> commentLikes,
        List<RecentArticle> articleViews
) {
}

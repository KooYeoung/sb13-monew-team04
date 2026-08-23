package com.codeit.sb13.monew.notification.service;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationFindDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    void notifyArticlesForInterest(ArticlesForInterestDto request);

    void notifyCommentLiked(CommentLikedDto request);

    CursorPageResponseDto<NotificationResult> findAllNotifications(NotificationFindDto request);

    NotificationResult confirmNotification(UUID notificationId, UUID userId);

    List<NotificationResult> confirmAllNotifications(UUID userId);
}

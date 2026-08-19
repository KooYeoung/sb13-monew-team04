package com.codeit.sb13.monew.notification.service;

import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;

public interface NotificationService {

    void notifyArticlesForInterest(ArticlesForInterestDto request);

    void notifyCommentLiked(CommentLikedDto request);
}

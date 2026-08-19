package com.codeit.sb13.monew.notification.service.impl;

import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.ResourceType;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void notifyArticlesForInterest(ArticlesForInterestDto request) {
        if(request.recipients()==null || request.recipients().isEmpty()) {
            return;
        }
        String content = "[" + request.interestName() + "]와 관련된 기사가 " + request.articleCount() + "건 등록되었습니다.";

        List<Notification> notifications = request.recipients().stream()
                        .map(recipient -> Notification.create(recipient, content, request.resourceId(), ResourceType.INTEREST))
                        .toList();

        notificationRepository.saveAll(notifications);
    }

    @Override
    @Transactional
    public void notifyCommentLiked(CommentLikedDto request) {
        if(request.sender()==null) {
            return;
        }
        String content = "[" + request.sender().getNickname() + "]님이 나의 댓글을 좋아합니다.";

        Notification notification = Notification.create(request.recipient(), content, request.resourceId(), ResourceType.COMMENT);
        notificationRepository.saveAll(List.of(notification));
    }
}

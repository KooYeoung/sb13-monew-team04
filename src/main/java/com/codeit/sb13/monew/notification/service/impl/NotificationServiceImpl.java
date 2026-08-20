package com.codeit.sb13.monew.notification.service.impl;

import com.codeit.sb13.monew.global.exception.notification.NotificationNotFoundException;
import com.codeit.sb13.monew.global.exception.user.UserNotFoundException;
import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.ResourceType;
import com.codeit.sb13.monew.notification.mapper.NotificationMapper;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;
import com.codeit.sb13.monew.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper mapper;

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

    @Override
    @Transactional
    public NotificationResult confirmNotification(UUID notificationId, UUID userId) {
        if(!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(()->new NotificationNotFoundException(notificationId));

        if(!notification.getUser().getId().equals(userId)) {
            log.warn("본인 소유가 아닌 알림 확인 시도 - notificationId={}, userId={}", notificationId, userId);
            throw new NotificationNotFoundException(notificationId);
        }

        notification.confirm();
        notificationRepository.save(notification);
        return mapper.toResult(notification);
    }

    @Override
    @Transactional
    public List<NotificationResult> confirmAllNotifications(UUID userId) {
        if(!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        List<Notification> notifications = notificationRepository.findByUser_IdAndConfirmedFalse(userId);
        notifications.forEach(Notification::confirm);

        notificationRepository.saveAll(notifications);
        return notifications.stream().map(mapper::toResult).toList();
    }
}

package com.codeit.sb13.monew.notification.service.impl;

import com.codeit.sb13.monew.global.dto.CursorPageResponseDto;
import com.codeit.sb13.monew.global.exception.notification.NotificationNotFoundException;
import com.codeit.sb13.monew.notification.domain.Notification;
import com.codeit.sb13.monew.notification.domain.ResourceType;
import com.codeit.sb13.monew.notification.mapper.NotificationMapper;
import com.codeit.sb13.monew.notification.repository.NotificationRepository;
import com.codeit.sb13.monew.notification.repository.dto.NotificationFindCondition;
import com.codeit.sb13.monew.notification.service.NotificationService;
import com.codeit.sb13.monew.notification.service.dto.ArticlesForInterestDto;
import com.codeit.sb13.monew.notification.service.dto.CommentLikedDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationFindDto;
import com.codeit.sb13.monew.notification.service.dto.NotificationResult;
import com.codeit.sb13.monew.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;
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
    public CursorPageResponseDto<NotificationResult> findAllNotifications(NotificationFindDto request) {
        userService.validateExists(request.userId());

        List<Notification> fetched = notificationRepository.findUnconfirmedByUserWithCursor(
                new NotificationFindCondition(request.userId(), request.cursorId(), request.after(), request.limit() + 1));

        boolean hasNext = fetched.size() > request.limit();
        List<Notification> content = hasNext ? fetched.subList(0, request.limit()) : fetched;

        long totalElements = notificationRepository.countByUser_IdAndConfirmedFalse(request.userId());

        String nextCursor = null;
        String nextAfter = null;
        if (hasNext) {
            Notification last = content.get(content.size() - 1);
            nextCursor = last.getId().toString();
            nextAfter = last.getCreatedAt().toString();
        }

        List<NotificationResult> results = content.stream()
                .map(mapper::toResult)
                .toList();

        return new CursorPageResponseDto<>(results, nextCursor, nextAfter, null,content.size(), totalElements, hasNext);
    }


    @Override
    @Transactional
    public NotificationResult confirmNotification(UUID notificationId, UUID userId) {
        userService.validateExists(userId);
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
        userService.validateExists(userId);

        List<Notification> targets = notificationRepository.findByUser_IdAndConfirmedFalse(userId);
        if (targets.isEmpty()) {
            return List.of();
        }

        List<UUID> targetIds = targets.stream().map(Notification::getId).toList();
        LocalDateTime now = LocalDateTime.now();
        notificationRepository.confirmAllByUserId(userId, targetIds, now);
        targets.forEach(n -> n.confirm(now));

        return targets.stream().map(mapper::toResult).toList();
    }

    @Override
    @Transactional
    public void deleteConfirmedNotification() {
        int deletedCount = notificationRepository.deleteConfirmedBefore(LocalDateTime.now().minusDays(7));
        log.info("확인 처리된 지 7일 경과한 알림 {}건 삭제", deletedCount);
    }

}

package com.codeit.sb13.monew.notification.scheduler;

import com.codeit.sb13.monew.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void deleteNotification() {
        try {
            notificationService.deleteConfirmedNotification();
        } catch (Exception e) {
            log.error("알림 정리 배치 실행 중 오류 발생", e);
        }
    }
}

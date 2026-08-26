package com.codeit.sb13.monew.notification.scheduler;

import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import com.codeit.sb13.monew.notification.scheduler.config.NotificationScheduleProperties;
import com.codeit.sb13.monew.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final String LOCK_KEY = "notification-delete";

    private final NotificationService notificationService;
    private final NotificationScheduleProperties props;
    private final AdvisoryLockService advisoryLockService;

    @Scheduled(cron = "${monew.notification.schedule.cron}", zone = "Asia/Seoul")
    public void deleteNotification() {
        if (!props.enabled()) {
            log.info("알림 정리 스케줄러가 비활성화되어 작업을 건너뜁니다.");
            return;
        }

        boolean executed = advisoryLockService.executeWithLock(LOCK_KEY, notificationService::deleteConfirmedNotification);
        if (!executed) {
            log.warn("이미 다른 스케줄러가 알림 정리를 실행 중입니다.");
            return;
        }

        log.info("알림 정리 스케줄러 작업을 완료했습니다.");
    }
}

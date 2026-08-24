package com.codeit.sb13.monew.article.schedule;

import com.codeit.sb13.monew.article.s3.service.ArticleBackupService;
import com.codeit.sb13.monew.article.schedule.config.ArticleScheduleProperties;
import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackupJobService {

    private static final String LOCK_KEY_PREFIX = "article-backup";

    private final ArticleBackupService articleBackupService;
    private final ArticleScheduleProperties props;
    private final AdvisoryLockService advisoryLockService;
    private final Clock clock;

    @Scheduled(cron = "${monew.backup.schedule.cron}")
    public void backupPreviousDayArticles() {
        if (!props.enabled()) {
            log.info("기사 백업 스케줄러가 비활성화되어 작업을 건너뜁니다.");
            return;
        }

        LocalDate backupDate = previousDate();
        String lockKey = lockKey(backupDate);

        boolean executed = advisoryLockService.executeWithLock(
                lockKey,
                () -> articleBackupService.backupArticlesByDate(backupDate)
        );
        if (!executed) {
            log.warn("이미 다른 스케줄러가 기사 백업을 실행 중입니다. backupDate={}", backupDate);
            return;
        }

        log.info("기사 백업 스케줄러 작업을 완료했습니다. backupDate={}", backupDate);
    }

    private LocalDate previousDate() {
        return LocalDate.now(clock).minusDays(1);
    }

    private String lockKey(LocalDate backupDate) {
        return LOCK_KEY_PREFIX + ":" + backupDate;
    }
}

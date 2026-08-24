package com.codeit.sb13.monew.article.schedule;

import com.codeit.sb13.monew.article.s3.service.ArticleBackupService;
import com.codeit.sb13.monew.article.schedule.config.ArticleScheduleProperties;
import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("기사 백업 스케줄 작업 단위 테스트")
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class BackupJobServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);
    private static final LocalDate BACKUP_DATE = LocalDate.of(2026, 8, 23);
    private static final String CRON = "0 10 0 * * *";

    @Mock
    private ArticleBackupService articleBackupService;

    @Mock
    private AdvisoryLockService advisoryLockService;

    @Test
    @DisplayName("스케줄이 비활성화되어 있으면 백업을 수행하지 않는다")
    void skipsWhenScheduleDisabled(CapturedOutput output) {
        BackupJobService service = service(false);

        service.backupPreviousDayArticles();

        verifyNoInteractions(advisoryLockService, articleBackupService);
        assertThat(output).contains("기사 백업 스케줄러가 비활성화되어 작업을 건너뜁니다.");
    }

    @Test
    @DisplayName("락을 획득하면 전날 날짜의 기사 백업을 수행한다")
    void backsUpPreviousDayWhenLockAcquired() {
        BackupJobService service = service(true);
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });

        service.backupPreviousDayArticles();

        ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(advisoryLockService).executeWithLock(lockKeyCaptor.capture(), any(Runnable.class));
        assertThat(lockKeyCaptor.getValue()).isEqualTo("article-backup:2026-08-23");
        verify(articleBackupService).backupArticlesByDate(BACKUP_DATE);
    }

    @Test
    @DisplayName("락을 획득하지 못하면 경고 로그를 남기고 백업을 수행하지 않는다")
    void skipsWhenLockNotAcquired(CapturedOutput output) {
        BackupJobService service = service(true);
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class))).thenReturn(false);

        service.backupPreviousDayArticles();

        verify(articleBackupService, never()).backupArticlesByDate(any(LocalDate.class));
        assertThat(output).contains(
                "이미 다른 스케줄러가 기사 백업을 실행 중입니다.",
                "backupDate=2026-08-23"
        );
    }

    @Test
    @DisplayName("백업 중 예외가 발생하면 그대로 전파한다")
    void propagatesBackupFailure() {
        BackupJobService service = service(true);
        RuntimeException cause = new RuntimeException("backup failure");
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });
        doThrow(cause).when(articleBackupService).backupArticlesByDate(BACKUP_DATE);

        assertThatThrownBy(service::backupPreviousDayArticles)
                .isSameAs(cause);
    }

    private BackupJobService service(boolean enabled) {
        return new BackupJobService(
                articleBackupService,
                new ArticleScheduleProperties(enabled, CRON),
                advisoryLockService,
                fixedClock()
        );
    }

    private Clock fixedClock() {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(TODAY.atStartOfDay(zone).toInstant(), zone);
    }
}

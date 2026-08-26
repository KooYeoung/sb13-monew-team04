package com.codeit.sb13.monew.notification.scheduler;

import com.codeit.sb13.monew.global.service.AdvisoryLockService;
import com.codeit.sb13.monew.notification.scheduler.config.NotificationScheduleProperties;
import com.codeit.sb13.monew.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    private static final String CRON = "0 0 0 * * *";

    @Mock
    NotificationService notificationService;

    @Mock
    AdvisoryLockService advisoryLockService;

    NotificationScheduler notificationScheduler;

    private NotificationScheduler scheduler(boolean enabled) {
        return new NotificationScheduler(
                notificationService,
                new NotificationScheduleProperties(enabled, CRON),
                advisoryLockService
        );
    }

    @BeforeEach
    void setUp() {
        notificationScheduler = scheduler(true);
    }

    @Test
    @DisplayName("스케줄러가 비활성화되어 있으면 정리 작업을 수행하지 않는다.")
    void 비활성화시_정리_미수행() {
        NotificationScheduler disabled = scheduler(false);

        disabled.deleteNotification();

        verifyNoInteractions(advisoryLockService, notificationService);
    }

    @Test
    @DisplayName("락을 획득하면 알림 삭제 서비스가 호출된다.")
    void 락_획득시_삭제_서비스_호출() {
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });

        notificationScheduler.deleteNotification();

        verify(notificationService).deleteConfirmedNotification();
    }

    @Test
    @DisplayName("락을 획득하지 못하면 삭제 서비스를 호출하지 않는다.")
    void 락_미획득시_삭제_서비스_미호출() {
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class))).thenReturn(false);

        notificationScheduler.deleteNotification();

        verify(notificationService, never()).deleteConfirmedNotification();
    }

    @Test
    @DisplayName("삭제 중 예외가 발생하면 그대로 전파한다.")
    void 삭제중_예외는_전파된다() {
        RuntimeException cause = new RuntimeException("DB 오류");
        doThrow(cause).when(notificationService).deleteConfirmedNotification();
        when(advisoryLockService.executeWithLock(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });

        assertThatThrownBy(() -> notificationScheduler.deleteNotification())
                .isSameAs(cause);
    }

    @Test
    @DisplayName("deleteNotification()은 매일 00:00(Asia/Seoul)에 실행되도록 스케줄링되어 있다.")
    void 스케줄_메타데이터_검증() throws NoSuchMethodException {
        Method method = NotificationScheduler.class.getDeclaredMethod("deleteNotification");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${monew.notification.schedule.cron}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
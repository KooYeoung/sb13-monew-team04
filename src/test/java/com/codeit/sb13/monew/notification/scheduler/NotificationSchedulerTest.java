package com.codeit.sb13.monew.notification.scheduler;

import com.codeit.sb13.monew.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    NotificationService notificationService;

    @InjectMocks
    NotificationScheduler notificationScheduler;

    @Test
    @DisplayName("스케줄러가 실행되면 알림 삭제 서비스가 호출된다.")
    void 정상_실행시_삭제_서비스_호출() {
        // when
        notificationScheduler.deleteNotification();

        // then
        verify(notificationService).deleteConfirmedNotification();
    }

    @Test
    @DisplayName("삭제 중 예외가 발생해도 스케줄러 밖으로 전파되지 않는다.")
    void 예외_발생해도_전파되지_않음() {
        // given
        doThrow(new RuntimeException("DB 오류")).when(notificationService).deleteConfirmedNotification();

        // when & then
        assertThatCode(() -> notificationScheduler.deleteNotification())
                .doesNotThrowAnyException();

        verify(notificationService).deleteConfirmedNotification();
    }

    @Test
    @DisplayName("deleteNotification()은 매일 00:00(Asia/Seoul)에 실행되도록 스케줄링되어 있다.")
    void 스케줄_메타데이터_검증() throws NoSuchMethodException {
        // given
        Method method = NotificationScheduler.class.getDeclaredMethod("deleteNotification");

        // when
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        // then
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}

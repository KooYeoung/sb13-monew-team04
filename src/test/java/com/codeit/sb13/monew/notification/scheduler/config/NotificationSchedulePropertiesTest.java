package com.codeit.sb13.monew.notification.scheduler.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("알림 정리 스케줄 설정 단위 테스트")
class NotificationSchedulePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationScheduleConfig.class);

    @Test
    @DisplayName("알림 정리 스케줄 설정 값을 바인딩한다")
    void bindsNotificationScheduleProperties() {
        contextRunner
                .withPropertyValues(
                        "monew.notification.schedule.enabled=true",
                        "monew.notification.schedule.cron=0 0 0 * * *"
                )
                .run(context -> {
                    NotificationScheduleProperties properties = context.getBean(NotificationScheduleProperties.class);

                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.cron()).isEqualTo("0 0 0 * * *");
                });
    }
}
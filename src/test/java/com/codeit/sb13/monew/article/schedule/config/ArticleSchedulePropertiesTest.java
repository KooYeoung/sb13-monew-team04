package com.codeit.sb13.monew.article.schedule.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("기사 스케줄 설정 단위 테스트")
class ArticleSchedulePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ArticleScheduleConfig.class);

    @Test
    @DisplayName("백업 스케줄 설정 값을 바인딩한다")
    void bindsArticleScheduleProperties() {
        contextRunner
                .withPropertyValues(
                        "monew.backup.schedule.enabled=true",
                        "monew.backup.schedule.cron=0 0 1 * * *"
                )
                .run(context -> {
                    ArticleScheduleProperties properties = context.getBean(ArticleScheduleProperties.class);

                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.cron()).isEqualTo("0 0 1 * * *");
                });
    }

    @Test
    @DisplayName("기사 수집 스케줄 설정 값을 바인딩한다")
    void bindsArticleCollectScheduleProperties() {
        contextRunner
                .withPropertyValues(
                        "monew.collect.schedule.enabled=true",
                        "monew.collect.schedule.cron=0 0/10 * * * *"
                )
                .run(context -> {
                    ArticleCollectScheduleProperties properties =
                            context.getBean(ArticleCollectScheduleProperties.class);

                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.cron()).isEqualTo("0 0/10 * * * *");
                });
    }
}
